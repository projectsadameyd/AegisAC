package dev.aegisac;
import java.util.*;import java.util.concurrent.ConcurrentHashMap;import org.bukkit.*;import org.bukkit.entity.Player;
/** Administrative exemptions only. No detector, movement witness, or cross-engine review. */
public final class NativeSession {
 private static final Map<UUID,Long> exemptions=new ConcurrentHashMap<>();
 private static final Map<UUID,Object> resetting=new ConcurrentHashMap<>();
 public static Object beginReset(UUID id){Object token=new Object();resetting.put(id,token);return token;}public static void finishReset(UUID id){resetting.remove(id);}public static void finishReset(UUID id,Object token){resetting.remove(id,token);}public static boolean resetting(UUID id){return resetting.containsKey(id);}
 public static void manualExempt(UUID id,long until){if(until<=System.currentTimeMillis())exemptions.remove(id);else exemptions.put(id,until);}
 public static boolean exempt(UUID id){Long until=exemptions.get(id);if(until==null)return false;if(until<=System.currentTimeMillis()){exemptions.remove(id,until);return false;}return true;}
 public static Map<UUID,Long> manualExemptions(){exemptions.keySet().removeIf(id->!exempt(id));return Map.copyOf(exemptions);}
 public static String health(UUID id){Player player=Bukkit.getPlayer(id);return player==null?"offline":"ping="+BukkitCompatibility.ping(player)+"ms, mode="+player.getGameMode()+", manualExempt="+exempt(id);}
 public static void forget(UUID id){exemptions.remove(id);resetting.remove(id);}public static void clear(){exemptions.clear();resetting.clear();}
}
