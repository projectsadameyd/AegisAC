package dev.aegisac;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import dev.aegisac.companion.*;
import io.papermc.paper.connection.*;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.*;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.io.*;
import java.net.URI;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.plugin.messaging.PluginMessageListener;

/** Pre-world admission. Reports come from the player's device and are not hardware attestation. */
final class CompanionGate implements Listener,PluginMessageListener,AutoCloseable {
    private static final String DEFAULT_DOWNLOAD="https://github.com/projectsadameyd/AegisAC/raw/refs/heads/development/r17-native-guard/r17/downloads/AegisAC-Companion-R17-preview2-hotfix2-1.21.11.jar";
    private static final Key LEAVE=Key.key("aegisac:leave_verification");
    private final AegisPlugin plugin;private final SecureRandom random=new SecureRandom();
    private final ConcurrentMap<PlayerConfigurationConnection,Pending> pending=new ConcurrentHashMap<>();
    private final Semaphore slots=new Semaphore(16);private final ConcurrentMap<String,Integer> addresses=new ConcurrentHashMap<>();
    private final AtomicLong passed=new AtomicLong(),matched=new AtomicLong(),failed=new AtomicLong(),replayed=new AtomicLong(),busy=new AtomicLong(),exempted=new AtomicLong();
    private volatile Settings settings;private volatile boolean closed;private volatile String startupError;
    private record Settings(boolean enabled,String download,int handshakeSeconds,int screenSeconds,Set<String> exemptNames){}
    private static final class Pending {
        final AdmissionProtocol.Challenge challenge;final CompletableFuture<AdmissionProtocol.Report> report=new CompletableFuture<>();final CompletableFuture<Boolean> leave=new CompletableFuture<>();
        Pending(AdmissionProtocol.Challenge c){challenge=c;}
    }
    CompanionGate(AegisPlugin plugin) {
        this.plugin=plugin;
        try{reload();}catch(IOException error){startupError="Invalid client-guard.yml; staff must correct the configuration.";settings=new Settings(true,DEFAULT_DOWNLOAD,12,30,Set.of());plugin.getLogger().severe(startupError+" "+error.getMessage());}
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin,AdmissionProtocol.CHALLENGE);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin,AdmissionProtocol.REPORT,this);
        plugin.getLogger().info("R17 companion admission="+(settings.enabled()?"REQUIRED":"DISABLED")+"; pre-world dialog; client reports are untrusted; AuthMe login is unchanged.");
    }
    synchronized void reload()throws IOException {
        File file=new File(plugin.getDataFolder(),"client-guard.yml");if(!file.exists())plugin.saveResource("client-guard.yml",false);
        YamlConfiguration yaml=VpnGate.readStrict(file);String url=yaml.getString("download-url",DEFAULT_DOWNLOAD);
        boolean migrateDownload=DEFAULT_DOWNLOAD.replace("preview2-hotfix2","preview2").equals(url)||DEFAULT_DOWNLOAD.replace("preview2-hotfix2","preview2-hotfix1").equals(url);
        if(migrateDownload)url=DEFAULT_DOWNLOAD;
        try{URI uri=URI.create(url);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||url.length()>1000)throw new IllegalArgumentException();}catch(IllegalArgumentException e){throw new IOException("download-url must be an HTTPS URL without credentials");}
        int handshake=yaml.getInt("handshake-timeout-seconds",12),screen=yaml.getInt("block-screen-seconds",30);
        if(handshake<5||handshake>20||screen<5||screen>40)throw new IOException("Handshake must be 5..20 seconds and block screen 5..40 seconds");
        Set<String> exemptNames=CompanionExemptions.read(yaml);
        if(migrateDownload){yaml.set("download-url",url);yaml.save(file);plugin.getLogger().info("Updated the Preview 2 companion download to the handshake hotfix.");}
        settings=new Settings(yaml.getBoolean("enabled",true),url,handshake,screen,exemptNames);startupError=null;
    }
    private synchronized boolean changeExemption(String username,boolean exempt)throws IOException {
        reload();
        boolean changed=CompanionExemptions.update(new File(plugin.getDataFolder(),"client-guard.yml"),username,exempt);
        if(changed)reload();
        return changed;
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void configure(AsyncPlayerConnectionConfigureEvent event) {
        Settings policy=settings;if(!policy.enabled())return;
        PlayerConfigurationConnection connection=event.getConnection();
        if(closed){connection.disconnect(message("Verification unavailable","Please reconnect after the server finishes restarting."));return;}
        if(startupError==null&&CompanionExemptions.contains(policy.exemptNames(),connection.getProfile().getName())){exempted.incrementAndGet();return;}
        if(!slots.tryAcquire()){busy.incrementAndGet();connection.disconnect(message("Verification busy","Please wait a few seconds and reconnect."));return;}
        String address=connection.getClientAddress().getAddress().getHostAddress();AtomicBoolean admitted=new AtomicBoolean();
        addresses.compute(address,(key,count)->{int n=count==null?0:count;if(n>=2)return n;admitted.set(true);return n+1;});
        if(!admitted.get()){slots.release();busy.incrementAndGet();connection.disconnect(message("Verification busy","Too many simultaneous checks from this connection. Please reconnect shortly."));return;}
        byte[] nonce=new byte[AdmissionProtocol.NONCE_BYTES];random.nextBytes(nonce);Pending session=new Pending(new AdmissionProtocol.Challenge(nonce));pending.put(connection,session);
        try {
            if(startupError!=null){block(connection,session,policy,"Verification unavailable",startupError);return;}
            connection.getAudience().showDialog(screen(policy,"Checking your client","AegisAC is checking loaded mod code for known prohibited client signatures. Your other files are not scanned. Please wait."));
            byte[] challengeBytes=AdmissionProtocol.encode(session.challenge);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(policy.handshakeSeconds());
            boolean challengeSent=false;
            AdmissionProtocol.Report report=null;
            while(!closed&&connection.isConnected()) {
                if(!challengeSent&&connection.getListeningPluginChannels().contains(AdmissionProtocol.CHALLENGE)) {
                    connection.sendPluginMessage(plugin,AdmissionProtocol.CHALLENGE,challengeBytes);challengeSent=true;
                }
                long remaining=deadline-System.nanoTime();if(remaining<=0)break;
                try{report=session.report.get(Math.min(remaining,TimeUnit.MILLISECONDS.toNanos(50)),TimeUnit.NANOSECONDS);break;}
                catch(TimeoutException waiting){/* A client may advertise its receiver after this event begins. */}
            }
            if(closed||!connection.isConnected())return;
            if(report==null){failed.incrementAndGet();plugin.getLogger().warning("Companion verification timed out for "+connection.getProfile().getName()+": challengeSent="+challengeSent+"; install preview2-hotfix2 and check the client log.");block(connection,session,policy,"Companion required","Install the AegisAC companion and Fabric API for Minecraft 1.21.11, then restart Minecraft and reconnect. If it is already installed, the check did not finish in time.");return;}
            if(closed||!connection.isConnected())return;
            if(!AdmissionProtocol.supported(report)){failed.incrementAndGet();block(connection,session,policy,"Companion update required","Install the matching AegisAC companion for this server, restart Minecraft, and reconnect.");return;}
            if(report.findings().stream().anyMatch(f->!CheatSignatures.known(f))){failed.incrementAndGet();block(connection,session,policy,"Verification failed","The companion reported an unsupported check result. Update the companion or contact server staff.");return;}
            if(report.outcome()==AdmissionProtocol.Outcome.KNOWN_MATCH){matched.incrementAndGet();String clients=String.join(", ",report.findings().stream().map(CheatSignatures::display).toList());
                plugin.getLogger().warning("Companion admission blocked "+connection.getProfile().getName()+": client-reported code signature "+clients+"; no ban added.");
                block(connection,session,policy,"Prohibited client reported","Detected code signature: "+clients+". Remove the prohibited client, restart Minecraft, and reconnect. Contact staff if this result is unexpected.");return;}
            if(report.outcome()!=AdmissionProtocol.Outcome.NO_KNOWN_MATCH||report.roots()==0){failed.incrementAndGet();block(connection,session,policy,"Verification incomplete","The companion could not finish checking loaded mods. Restart Minecraft and try again. This is not a cheat conviction.");return;}
            connection.getAudience().closeDialog();passed.incrementAndGet();
            plugin.getLogger().info("Companion report accepted for "+connection.getProfile().getName()+": no known signature reported; roots="+report.roots()+" scan="+report.millis()+"ms. AuthMe authentication still applies.");
        }catch(InterruptedException error){Thread.currentThread().interrupt();connection.disconnect(message("Verification interrupted","Please reconnect."));}
        catch(ExecutionException|IOException|RuntimeException error){failed.incrementAndGet();plugin.getLogger().log(java.util.logging.Level.WARNING,"Companion admission could not complete for "+connection.getProfile().getName(),error);if(connection.isConnected())connection.disconnect(message("Verification failed","Restart Minecraft with the current AegisAC companion and reconnect. Contact staff if this repeats."));}
        finally {pending.remove(connection,session);addresses.computeIfPresent(address,(key,count)->count<=1?null:count-1);slots.release();}
    }
    private void block(PlayerConfigurationConnection connection,Pending session,Settings policy,String title,String body)throws InterruptedException {
        if(!connection.isConnected())return;connection.getAudience().showDialog(screen(policy,title,body));
        try{session.leave.get(policy.screenSeconds(),TimeUnit.SECONDS);}catch(ExecutionException|TimeoutException ignored){}
        if(connection.isConnected()){connection.getAudience().closeDialog();connection.disconnect(message(title,body+"\n\nDownload: "+policy.download()));}
    }
    private Dialog screen(Settings policy,String title,String body) {
        return Dialog.create(factory->factory.empty().base(DialogBase.builder(Component.text("AegisAC",NamedTextColor.GOLD))
                .canCloseWithEscape(false).pause(false).afterAction(DialogBase.DialogAfterAction.NONE)
                .body(List.of(DialogBody.plainMessage(Component.text(title,NamedTextColor.YELLOW)),DialogBody.plainMessage(Component.text(body,NamedTextColor.GRAY)),
                    DialogBody.plainMessage(Component.text("Download opens your browser. Put the mod in your Minecraft mods folder and restart. This screen cannot install Java mods.",NamedTextColor.DARK_GRAY))))
                .build())
            .type(DialogType.multiAction(List.of(ActionButton.builder(Component.text("Download companion",NamedTextColor.GOLD)).width(180)
                    .action(DialogAction.staticAction(ClickEvent.openUrl(policy.download()))).build(),
                ActionButton.builder(Component.text("Fabric API",NamedTextColor.GRAY)).width(100)
                    .action(DialogAction.staticAction(ClickEvent.openUrl("https://modrinth.com/mod/fabric-api/versions?g=1.21.11"))).build()),
                ActionButton.builder(Component.text("Disconnect",NamedTextColor.RED)).action(DialogAction.customClick(LEAVE,null)).build(),2)));
    }
    private static Component message(String title,String body){return Component.text("AegisAC\n",NamedTextColor.GOLD).append(Component.text(title+"\n\n",NamedTextColor.YELLOW)).append(Component.text(body,NamedTextColor.GRAY));}
    @Override public void onPluginMessageReceived(String channel,Player player,byte[] bytes){/* Admission is completed before a Player exists. Play-state reports never grant entry. */}
    @Override public void onPluginMessageReceived(String channel,PlayerConnection sender,byte[] bytes) {
        if(!channel.equals(AdmissionProtocol.REPORT)||!(sender instanceof PlayerConfigurationConnection connection))return;
        Pending session=pending.get(connection);if(session==null||session.report.isDone())return;
        try {AdmissionProtocol.Report report=AdmissionProtocol.report(bytes);if(!AdmissionProtocol.matches(session.challenge,report)){replayed.incrementAndGet();return;}session.report.complete(report);}
        catch(IOException|RuntimeException e){session.report.completeExceptionally(new IOException("Malformed companion report"));}
    }
    @EventHandler public void click(PlayerCustomClickEvent event){if(event.getIdentifier().equals(LEAVE)&&event.getCommonConnection() instanceof PlayerConfigurationConnection c){Pending p=pending.get(c);if(p!=null){p.leave.complete(true);p.report.completeExceptionally(new IOException("Player disconnected"));}}}
    @EventHandler public void disconnect(PlayerConnectionCloseEvent event){pending.forEach((c,p)->{if(!c.isConnected()){p.leave.complete(true);p.report.completeExceptionally(new IOException("Connection closed"));}});}
    boolean command(CommandSender sender,String[] args) {
        if(args.length==0||!args[0].equalsIgnoreCase("client"))return false;
        if(!sender.hasPermission("aegisac.admin")){sender.sendMessage("AegisAC � Permission denied.");return true;}
        if(args.length>=2&&args[1].equalsIgnoreCase("exempt")) {
            if(args.length==3&&args[2].equalsIgnoreCase("list")) {
                Set<String> names=new TreeSet<>(settings.exemptNames());
                sender.sendMessage("AegisAC � Companion exemptions ("+names.size()+"): "+(names.isEmpty()?"none":String.join(", ",names)));return true;
            }
            if(args.length==4&&(args[2].equalsIgnoreCase("add")||args[2].equalsIgnoreCase("remove"))) {
                boolean add=args[2].equalsIgnoreCase("add");
                try {
                    boolean changed=changeExemption(args[3],add);
                    sender.sendMessage("AegisAC � "+args[3]+(add?(changed?" added to":" is already on"):(changed?" removed from":" is not on"))+" the companion exemption list. Applies on their next connection.");
                    sender.sendMessage("AegisAC � AuthMe login and normal anticheat checks still apply.");
                }catch(IOException|IllegalArgumentException error){sender.sendMessage("AegisAC � Could not update companion exemptions: "+error.getMessage());}
                return true;
            }
            sender.sendMessage("AegisAC � /aegis client exempt add <username> | remove <username> | list");return true;
        }
        if(args.length==2&&args[1].equalsIgnoreCase("reload")){try{reload();sender.sendMessage("AegisAC � Companion admission configuration reloaded; applies to new connections.");}catch(IOException e){sender.sendMessage("AegisAC � Client guard reload rejected: "+e.getMessage());}return true;}
        sender.sendMessage("AegisAC � Companion required="+settings.enabled()+" startupError="+(startupError!=null)+" AuthMePresent="+Bukkit.getPluginManager().isPluginEnabled("AuthMe"));
        sender.sendMessage("AegisAC � Pending="+pending.size()+" acceptedReports="+passed+" signatureBlocks="+matched+" incompleteOrMissing="+failed+" wrongNonce="+replayed+" busy="+busy+" exemptJoins="+exempted+" exemptUsernames="+settings.exemptNames().size());
        sender.sendMessage("AegisAC � Checks=known loaded-code signatures; client reports can be forged. No automatic client bans. Download="+settings.download());return true;
    }
    @Override public void close(){closed=true;pending.forEach((c,p)->{p.leave.complete(true);p.report.completeExceptionally(new IOException("Server stopping"));if(c.isConnected())c.disconnect(message("Server restarting","Please reconnect shortly."));});Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin,AdmissionProtocol.REPORT,this);Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin,AdmissionProtocol.CHALLENGE);HandlerList.unregisterAll(this);}
}
