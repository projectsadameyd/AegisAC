package dev.aegisac;

import ac.grim.grimac.platform.bukkit.GrimACBukkitLoaderPlugin;
import java.io.File;
import java.io.IOException;
import net.donutac.anticheat.DonutAntiCheat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.HandlerList;

/** R17 integration: preserves the R16 detection stack and all stored sanction/connection settings. */
public final class AegisPlugin extends GrimACBukkitLoaderPlugin {
    private static AegisPlugin instance;
    private DonutAntiCheat donut;
    private VpnGate vpn;
    private NativeGuard guard;
    private CompanionGate companion;
    public static AegisPlugin instance(){return instance;}
    public DonutAntiCheat donut(){return donut;}
    VpnGate vpn(){return vpn;}
    NativeGuard guard(){return guard;}
    CompanionGate companion(){return companion;}
    @Override public void onLoad() {
        instance=this;NativeConfig.install(this);
        try{PresentationConfig.migrate(getDataFolder().toPath());}
        catch(IOException error){throw new IllegalStateException("Cannot migrate AegisAC presentation settings",error);}
        super.onLoad();
    }
    void reloadConnectionGuard()throws IOException {
        if(vpn==null){vpn=new VpnGate(this);Bukkit.getPluginManager().registerEvents(vpn,this);}else vpn.reload();
    }
    @Override public void onEnable() {
        super.onEnable();
        try{reloadConnectionGuard();}catch(IOException error){getLogger().warning("Connection screening unavailable; correct anti-vpn.yml and use /aegis reload.");}
        AegisGrimAddon.install(this);donut=new DonutAntiCheat();donut.onEnable();AegisGrimAddon.connectDonut(donut);
        if(!new File(getDataFolder(),"aegis-logo.png").exists())saveResource("aegis-logo.png",false);
        AegisGrimAddon.configureBranding();
        try{guard=new NativeGuard(this);}catch(IOException error){getLogger().severe("R17 interaction protection did not enable: "+error.getMessage());}
        companion=new CompanionGate(this);
        getLogger().info("R17-COMPANION2-HF1: AegisAC detection, remote interaction protection and required companion admission. Use /aegis client status.");
        getServer().getScheduler().runTaskTimer(this,()->getServer().broadcastMessage(ChatColor.translateAlternateColorCodes('&',"&eMade by @_adam814")),12000,12000);
    }
    @Override public void onDisable() {
        if(companion!=null){companion.close();companion=null;}
        if(guard!=null){guard.close();guard=null;}
        if(vpn!=null){HandlerList.unregisterAll(vpn);vpn.close();vpn=null;}
        if(donut!=null){HandlerList.unregisterAll(donut);getServer().getScheduler().cancelTasks(donut);donut.onDisable();}
        AegisGrimAddon.shutdown();
        try{super.onDisable();}finally{instance=null;}
    }
}
