package dev.aegisac;
import net.donutac.anticheat.DonutAntiCheat;import org.bukkit.entity.Player;
/** Donut assists through its own configured rule thresholds. Grim never waits for it. */
public final class DonutEvidenceBridge {private DonutEvidenceBridge(){}public static void accept(int vl,Player player,String key,String detail,DonutAntiCheat owner){if(vl>0&&player!=null)AegisGrimAddon.acceptDonut(player,key,vl,detail,owner);}}
