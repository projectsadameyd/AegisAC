package dev.aegisac;

import ac.grim.grimac.shaded.com.github.retrooper.packetevents.PacketEvents;
import ac.grim.grimac.shaded.com.github.retrooper.packetevents.event.*;
import ac.grim.grimac.shaded.com.github.retrooper.packetevents.protocol.packettype.PacketType;
import ac.grim.grimac.shaded.com.github.retrooper.packetevents.wrapper.play.client.*;
import ac.grim.grimac.shaded.com.github.retrooper.packetevents.wrapper.play.server.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.util.*;

/** Additional action protection around the unchanged R16 stack. No filesystem or mod-name detection. */
final class NativeGuard implements Listener,AutoCloseable {
    private final AegisPlugin plugin;
    private final ConcurrentMap<UUID,Tracked> players=new ConcurrentHashMap<>();
    private final ArrayBlockingQueue<Pending> actions=new ArrayBlockingQueue<>(256);
    private final AegisAntiXray xray=new AegisAntiXray();
    private final AtomicLong blocked=new AtomicLong(),kicks=new AtomicLong(),overload=new AtomicLong(),decoderErrors=new AtomicLong();
    private volatile YamlConfiguration config;
    private volatile RemoteInteractionRules.Settings policy;
    private volatile boolean enabled,kickAction,walls;
    private PacketListenerAbstract packets;
    private int task=-1,cursor;
    private long lastTick,visibilityNanos;
    private record Pending(UUID id,RemoteInteractionRules.Kind kind,RemoteInteractionRules.Bounds target,RemoteInteractionRules.Decision decision,long time) {}
    private static final class Tracked {
        final RemoteInteractionRules rules;
        final AtomicBoolean pending=new AtomicBoolean();
        volatile UUID world;
        Tracked(RemoteInteractionRules.Settings policy){rules=new RemoteInteractionRules(policy);}
    }
    NativeGuard(AegisPlugin plugin)throws IOException {
        this.plugin=plugin;reload();
        Bukkit.getPluginManager().registerEvents(this,plugin);
        packets=new PacketListenerAbstract(PacketListenerPriority.LOWEST) {
            @Override public void onPacketReceive(PacketReceiveEvent event) {
                if(!enabled)return;
                UUID id=event.getUser().getUUID();Tracked t=id==null?null:players.get(id);
                if(t==null||t.world==null||NativeSession.exempt(id))return;
                long now=System.nanoTime();
                try {
                    ac.grim.grimac.shaded.com.github.retrooper.packetevents.util.Vector3i target=null;
                    if(event.getPacketType()==PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
                        var p=new WrapperPlayClientPlayerBlockPlacement(event);
                        if(p.getFaceId()>=0&&p.getFaceId()<=5)target=p.getBlockPosition();
                    }else if(event.getPacketType()==PacketType.Play.Client.PLAYER_DIGGING) {
                        var p=new WrapperPlayClientPlayerDigging(event);
                        if(p.getAction()==ac.grim.grimac.shaded.com.github.retrooper.packetevents.protocol.player.DiggingAction.START_DIGGING)target=p.getBlockPosition();
                    }
                    if(target!=null) {
                        var bounds=RemoteInteractionRules.Bounds.block(target.getX(),target.getY(),target.getZ());
                        var decision=t.rules.evaluate(RemoteInteractionRules.Kind.BLOCK,t.world,bounds,now);
                        if(decision.cancel()){event.setCancelled(true);blocked.incrementAndGet();enqueue(id,t,RemoteInteractionRules.Kind.BLOCK,bounds,decision,now);}
                    }
                }catch(RuntimeException malformed){decoderErrors.incrementAndGet();t.rules.reset(now,1_000_000_000L);}
            }
            @Override public void onPacketSend(PacketSendEvent event) {
                UUID id=event.getUser().getUUID();Tracked t=id==null?null:players.get(id);if(t==null||event.isCancelled())return;
                if(event.getPacketType()==PacketType.Play.Server.PLAYER_POSITION_AND_LOOK)t.rules.reset(System.nanoTime(),grace("teleport-grace-ms",2500));
                else if(event.getPacketType()==PacketType.Play.Server.EXPLOSION)t.rules.reset(System.nanoTime(),2_000_000_000L);
                else if(event.getPacketType()==PacketType.Play.Server.ENTITY_VELOCITY)try {
                    if(new WrapperPlayServerEntityVelocity(event).getEntityId()==event.getUser().getEntityId())t.rules.reset(System.nanoTime(),1_500_000_000L);
                }catch(RuntimeException ignored){t.rules.reset(System.nanoTime(),1_000_000_000L);}
            }
        };
        PacketEvents.getAPI().getEventManager().registerListener(packets);
        for(Player p:Bukkit.getOnlinePlayers())track(p);
        task=Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin,this::tick,1,1);
        if(config.getBoolean("anti-xray.auto-configure",true))installXray();
    }
    private long grace(String key,int fallback){return Math.min(10000,Math.max(1000,config.getInt(key,fallback)))*1_000_000L;}
    void reload()throws IOException {
        File file=new File(plugin.getDataFolder(),"r17-guard.yml");if(!file.exists())plugin.saveResource("r17-guard.yml",false);
        YamlConfiguration next=VpnGate.readStrict(file);
        String action=next.getString("action","KICK").toUpperCase(Locale.ROOT);
        if(!Set.of("KICK","CANCEL").contains(action))throw new IOException("R17 action must be KICK or CANCEL");
        RemoteInteractionRules.Settings settings;
        try{settings=new RemoteInteractionRules.Settings(next.getDouble("reach-allowance",1.25),next.getDouble("kick-excess",2),next.getInt("confirmations",3),next.getInt("max-ping-ms",200),next.getLong("confirmation-window-ms",4000)*1_000_000L);}
        catch(IllegalArgumentException error){throw new IOException(error.getMessage(),error);}
        int engine=next.getInt("anti-xray.engine-mode",3);if(engine!=2&&engine!=3)throw new IOException("Anti-Xray mode must be 2 or 3");
        String screen=next.getString("messages.kicked","");if(screen.isBlank()||screen.length()>3000)throw new IOException("R17 kick message must be 1..3000 characters");
        config=next;policy=settings;enabled=next.getBoolean("enabled",true);kickAction=action.equals("KICK");walls=next.getBoolean("block-through-walls",true);
        actions.clear();players.values().forEach(t->{t.pending.set(false);t.rules.configure(settings);t.rules.reset(System.nanoTime(),2_000_000_000L);});
    }
    private void track(Player p) {
        if(players.size()>=512){overload.incrementAndGet();return;}
        Tracked t=new Tracked(policy);t.world=p.getWorld().getUID();t.rules.reset(System.nanoTime(),grace("join-grace-ms",5000));players.put(p.getUniqueId(),t);
    }
    private void tick() {
        long now=System.nanoTime();double tickMillis=lastTick==0?50:(now-lastTick)/1_000_000.0;lastTick=now;visibilityNanos=0;
        List<Player> online=new ArrayList<>(Bukkit.getOnlinePlayers());long budget=now+3_000_000L;
        if(!online.isEmpty())for(int i=0;i<Math.min(512,online.size())&&System.nanoTime()<budget;i++) {
            Player p=online.get(Math.floorMod(cursor++,online.size()));Tracked t=players.get(p.getUniqueId());if(t==null)continue;
            boolean eligible=enabled&&(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE)
                    &&!p.getAllowFlight()&&!p.isFlying()&&!p.isInsideVehicle()&&!p.isGliding()&&!p.isRiptiding()
                    &&!p.hasPermission("aegisac.guard.bypass")&&!p.hasPermission("grim.exempt")&&!p.hasPermission("grim.disabled")&&!NativeSession.exempt(p.getUniqueId());
            Location eye=p.getEyeLocation();UUID world=p.getWorld().getUID();
            if(t.world!=null&&!world.equals(t.world))t.rules.reset(now,grace("teleport-grace-ms",2500));t.world=world;
            if(tickMillis>75)t.rules.reset(now,1_000_000_000L);
            t.rules.sample(new RemoteInteractionRules.Pose(System.nanoTime(),world,new RemoteInteractionRules.Point(eye.getX(),eye.getY(),eye.getZ()),
                    attribute(p,Attribute.BLOCK_INTERACTION_RANGE,4.5),attribute(p,Attribute.ENTITY_INTERACTION_RANGE,3),BukkitCompatibility.ping(p),tickMillis,eligible));
        }
        for(int i=0;i<32&&System.nanoTime()<now+5_000_000L;i++){Pending pending=actions.poll();if(pending==null)break;apply(pending);}
    }
    private static double attribute(Player p,Attribute attr,double fallback){var value=p.getAttribute(attr);return value==null?fallback:value.getValue();}
    private void enqueue(UUID id,Tracked t,RemoteInteractionRules.Kind kind,RemoteInteractionRules.Bounds target,RemoteInteractionRules.Decision decision,long now) {
        if(!decision.kick()||!kickAction||!t.pending.compareAndSet(false,true))return;
        if(!actions.offer(new Pending(id,kind,target,decision,now))){t.pending.set(false);overload.incrementAndGet();t.rules.reset(now,1_000_000_000L);}
    }
    private void apply(Pending action) {
        Tracked t=players.get(action.id);if(t==null)return;
        try {
            long now=System.nanoTime();Player p=Bukkit.getPlayer(action.id);
            if(AegisGrimAddon.remoteActionSanctionsPaused()){t.rules.reset(now,1_000_000_000L);return;}
            if(!enabled||!kickAction||p==null||!p.isOnline()||now-action.time>1_000_000_000L||now<action.time
                    ||NativeSession.exempt(action.id)||p.hasPermission("aegisac.guard.bypass")||p.hasPermission("grim.exempt")||p.hasPermission("grim.disabled")
                    ||!t.rules.current(action.decision.epoch(),now)||BukkitCompatibility.ping(p)>policy.maxPing()
                    ||!p.getWorld().getUID().equals(t.world)||p.getGameMode()!=GameMode.SURVIVAL&&p.getGameMode()!=GameMode.ADVENTURE
                    ||p.getAllowFlight()||p.isInsideVehicle()||p.isGliding()||p.isRiptiding())return;
            // Permissions, attributes, teleports and current body position are rechecked before a disconnect.
            Location eye=p.getEyeLocation();double reach=attribute(p,action.kind==RemoteInteractionRules.Kind.BLOCK?Attribute.BLOCK_INTERACTION_RANGE:Attribute.ENTITY_INTERACTION_RANGE,action.kind==RemoteInteractionRules.Kind.BLOCK?4.5:3);
            if(action.target.distance(new RemoteInteractionRules.Point(eye.getX(),eye.getY(),eye.getZ()))<=reach+policy.allowance()+policy.kickExcess())return;
            String reference="R17-"+Long.toUnsignedString(System.currentTimeMillis(),36).toUpperCase(Locale.ROOT);
            String detail=String.format(Locale.ROOT,"%s: %s reach=%.2f limit=%.2f confirmations=%d",reference,action.kind,action.decision.distance(),action.decision.limit(),action.decision.confirmations());
            kicks.incrementAndGet();AegisGrimAddon.remoteInteractionKicked(p,detail);
            p.kickPlayer(ChatColor.translateAlternateColorCodes('&',config.getString("messages.kicked","").replace("{reference}",reference)));
            t.rules.reset(now,5_000_000_000L);
        }finally{t.pending.set(false);}
    }
    private boolean check(Player p,RemoteInteractionRules.Kind kind,RemoteInteractionRules.Bounds target) {
        Tracked t=players.get(p.getUniqueId());if(t==null||!enabled||NativeSession.exempt(p.getUniqueId())||p.hasPermission("aegisac.guard.bypass"))return false;
        long now=System.nanoTime();var decision=t.rules.evaluate(kind,p.getWorld().getUID(),target,now);
        if(decision.cancel()){blocked.incrementAndGet();enqueue(p.getUniqueId(),t,kind,target,decision,now);return true;}
        return false;
    }
    private boolean visible(Player p,RemoteInteractionRules.Bounds target,org.bukkit.block.Block targetBlock) {
        if(!walls)return true;
        if(visibilityNanos>=2_000_000L){overload.incrementAndGet();return true;}
        long start=System.nanoTime();try{return visibleGeometry(p,target,targetBlock,start);}finally{visibilityNanos+=System.nanoTime()-start;}
    }
    private boolean visibleGeometry(Player p,RemoteInteractionRules.Bounds target,org.bukkit.block.Block targetBlock,long started) {
        Tracked t=players.get(p.getUniqueId());long now=System.nanoTime();
        if(!walls||t==null||!t.rules.current(t.rules.epoch(),now)||p.hasPermission("aegisac.guard.bypass")||NativeSession.exempt(p.getUniqueId()))return true;
        // A single visible ray is sufficient. Never kick on occlusion evidence alone.
        double midX=(target.minX()+target.maxX())/2,midY=(target.minY()+target.maxY())/2,midZ=(target.minZ()+target.maxZ())/2;
        double[][] destinations={{midX,midY,midZ},{target.minX()+.05,midY,target.minZ()+.05},{target.maxX()-.05,midY,target.maxZ()-.05},{midX,target.maxY()-.05,midZ},{midX,target.minY()+.05,midZ}};
        List<RemoteInteractionRules.Pose> poses=t.rules.history();int rays=0;
        for(int i=poses.size()-1;i>=0;i-=4) {
            var pose=poses.get(i);if(!pose.world().equals(p.getWorld().getUID())||now-pose.nanos()>600_000_000L)continue;
            Location from=new Location(p.getWorld(),pose.eye().x(),pose.eye().y(),pose.eye().z());
            for(double[] point:destinations) {
                if(++rays>20||System.nanoTime()-started+visibilityNanos>2_000_000L)return true;
                org.bukkit.util.Vector direction=new org.bukkit.util.Vector(point[0]-from.getX(),point[1]-from.getY(),point[2]-from.getZ());double length=direction.length();
                if(length<.05||length>10)return true;
                // Refuse to query/load any unloaded chunk on the ray path.
                for(int n=0;n<=16;n++){double alpha=n/16.0;int x=(int)Math.floor(from.getX()+direction.getX()*alpha),z=(int)Math.floor(from.getZ()+direction.getZ()*alpha);if(!p.getWorld().isChunkLoaded(x>>4,z>>4))return true;}
                RayTraceResult hit=p.getWorld().rayTraceBlocks(from,direction.normalize(),length+.02,FluidCollisionMode.NEVER,true);
                if(hit==null||hit.getHitBlock()==null||!hit.getHitBlock().getType().isOccluding())return true;
                if(targetBlock!=null&&hit.getHitBlock().equals(targetBlock))return true;
            }
        }
        blocked.incrementAndGet();return false;
    }
    private static RemoteInteractionRules.Bounds bounds(BoundingBox b){return new RemoteInteractionRules.Bounds(b.getMinX(),b.getMinY(),b.getMinZ(),b.getMaxX(),b.getMaxY(),b.getMaxZ());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void interact(PlayerInteractEvent e) {
        if(e.getClickedBlock()==null)return;var b=e.getClickedBlock();var target=RemoteInteractionRules.Bounds.block(b.getX(),b.getY(),b.getZ());
        if(check(e.getPlayer(),RemoteInteractionRules.Kind.BLOCK,target)||!visible(e.getPlayer(),target,b))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void interactEntity(PlayerInteractEntityEvent e){var target=bounds(e.getRightClicked().getBoundingBox());if(check(e.getPlayer(),RemoteInteractionRules.Kind.ENTITY,target)||!visible(e.getPlayer(),target,null))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void damage(EntityDamageByEntityEvent e){if(e.getDamager() instanceof Player p&&e.getCause()==EntityDamageEvent.DamageCause.ENTITY_ATTACK){var target=bounds(e.getEntity().getBoundingBox());if(check(p,RemoteInteractionRules.Kind.ENTITY,target)||!visible(p,target,null))e.setCancelled(true);}}
    @EventHandler public void join(PlayerJoinEvent e){track(e.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent e){players.remove(e.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void teleport(PlayerTeleportEvent e){reset(e.getPlayer(),grace("teleport-grace-ms",2500));}
    @EventHandler public void respawn(PlayerRespawnEvent e){reset(e.getPlayer(),grace("join-grace-ms",5000));}
    @EventHandler public void world(PlayerChangedWorldEvent e){reset(e.getPlayer(),grace("teleport-grace-ms",2500));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void velocity(PlayerVelocityEvent e){reset(e.getPlayer(),1_500_000_000L);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void damaged(EntityDamageEvent e){if(e.getEntity() instanceof Player p)reset(p,1_000_000_000L);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void placed(BlockPlaceEvent e){resetNear(e.getBlock().getLocation());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void broken(BlockBreakEvent e){resetNear(e.getBlock().getLocation());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void piston(BlockPistonExtendEvent e){resetNear(e.getBlock().getLocation());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void retract(BlockPistonRetractEvent e){resetNear(e.getBlock().getLocation());}
    @EventHandler public void worldLoaded(WorldLoadEvent e){if(config.getBoolean("anti-xray.auto-configure",true))installXray();}
    private void reset(Player p,long nanos){Tracked t=players.get(p.getUniqueId());if(t!=null)t.rules.reset(System.nanoTime(),nanos);}
    private void resetNear(Location at){for(Player p:at.getWorld().getPlayers())if(p.getLocation().distanceSquared(at)<100)reset(p,600_000_000L);}
    private void installXray(){int changed=xray.install(Bukkit.getWorlds(),config);if(changed>0)plugin.getLogger().warning("AegisAC anti-Xray profile saved for "+changed+" world(s). Fully restart the server to apply it; /reload is insufficient.");for(String error:xray.errors())plugin.getLogger().warning("AegisAC anti-Xray config: "+error);}
    String status(){return "R17 remote-action guard="+(enabled?"enabled":"disabled")+" action="+(kickAction?"KICK":"CANCEL")+" tracked="+players.size()+" blocked="+blocked+" kicks="+kicks+" pending="+actions.size()+" overload="+overload+" decodeUncertainty="+decoderErrors;}
    boolean command(CommandSender sender,String[] args) {
        if(args.length==0||!Set.of("guard","xray").contains(args[0].toLowerCase(Locale.ROOT)))return false;
        if(!sender.hasPermission("aegisac.admin")){sender.sendMessage("AegisAC: no permission.");return true;}
        try {
            String sub=args.length>1?args[1].toLowerCase(Locale.ROOT):"status";
            if(args[0].equalsIgnoreCase("xray")) {
                if(sub.equals("install"))installXray();else if(!sub.equals("status"))throw new IllegalArgumentException("/aegis xray status|install");
                sender.sendMessage("AegisAC: "+xray.status());
            }else {
                if(sub.equals("reload"))reload();else if(!sub.equals("status"))throw new IllegalArgumentException("/aegis guard status|reload");
                sender.sendMessage("AegisAC: "+status());
                sender.sendMessage("AegisAC: remote actions are observable; passive camera state and client Mods folders are not.");
            }
        }catch(IOException|IllegalArgumentException error){sender.sendMessage("AegisAC: "+error.getMessage());}
        return true;
    }
    @Override public void close(){enabled=false;if(task!=-1)Bukkit.getScheduler().cancelTask(task);if(packets!=null)PacketEvents.getAPI().getEventManager().unregisterListener(packets);HandlerList.unregisterAll(this);players.clear();actions.clear();}
}
