package dev.aegisac;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
/** Version-dependent presentation and Bukkit access. Native Grim detection is untouched. */
public final class BukkitCompatibility {
 private BukkitCompatibility(){}
 private record Key(Class<?> owner,String name,List<Class<?>> parameters){}
 private static final Map<Key,Optional<Method>> METHODS=new ConcurrentHashMap<>();
 private static Object invoke(Object target,String name,Class<?>[] parameters,Object... args){
  if(target==null)return null;
  Key key=new Key(target.getClass(),name,List.of(parameters));
  Optional<Method> method=METHODS.computeIfAbsent(key,k->{try{return Optional.of(k.owner().getMethod(k.name(),parameters));}catch(NoSuchMethodException missing){return Optional.empty();}});
  if(method.isEmpty())return null;
  try{return method.get().invoke(target,args);}catch(ReflectiveOperationException|LinkageError unavailable){return null;}
 }
 private static Object noArgs(Object target,String name){return invoke(target,name,new Class<?>[0]);}
 public static int ping(Player player){
  Object value=noArgs(player,"getPing");if(value instanceof Number n)return Math.max(0,n.intValue());
  Object handle=noArgs(player,"getHandle");if(handle!=null)try{return Math.max(0,handle.getClass().getField("ping").getInt(handle));}catch(ReflectiveOperationException ignored){}
  return -1;
 }
 public static double[] tps(){
  Object value=noArgs(Bukkit.getServer(),"getTPS");if(value instanceof double[] rates)return rates;
  Object server=noArgs(Bukkit.getServer(),"getServer");if(server!=null)try{Object rates=server.getClass().getField("recentTps").get(server);if(rates instanceof double[] array)return array;}catch(ReflectiveOperationException ignored){}
  return new double[]{Double.NaN};
 }
 public static void sendTitle(Player player,String title,String subtitle,int fadeIn,int stay,int fadeOut){
  Method full=METHODS.computeIfAbsent(new Key(player.getClass(),"sendTitle",List.of(String.class,String.class,int.class,int.class,int.class)),k->{try{return Optional.of(k.owner().getMethod(k.name(),String.class,String.class,int.class,int.class,int.class));}catch(NoSuchMethodException missing){return Optional.empty();}}).orElse(null);
  if(full!=null){try{full.invoke(player,title,subtitle,fadeIn,stay,fadeOut);return;}catch(ReflectiveOperationException|LinkageError ignored){}}
  Method shortForm=METHODS.computeIfAbsent(new Key(player.getClass(),"sendTitle",List.of(String.class,String.class)),k->{try{return Optional.of(k.owner().getMethod(k.name(),String.class,String.class));}catch(NoSuchMethodException missing){return Optional.empty();}}).orElse(null);
  if(shortForm!=null){try{shortForm.invoke(player,title,subtitle);return;}catch(ReflectiveOperationException|LinkageError ignored){}}
  player.sendMessage(title+" "+subtitle);
 }
 public static boolean isGliding(Player player){return Boolean.TRUE.equals(noArgs(player,"isGliding"));}
 public static boolean isInWater(Player player){Object value=noArgs(player,"isInWater");if(value instanceof Boolean b)return b;return player.getLocation().getBlock().getType().name().contains("WATER");}
 public static boolean isClimbing(Player player){Object value=noArgs(player,"isClimbing");if(value instanceof Boolean b)return b;String name=player.getLocation().getBlock().getType().name();return name.equals("LADDER")||name.equals("VINE");}
 public static PotionEffect speedEffect(Player player){for(PotionEffect effect:player.getActivePotionEffects())if(effect.getType().getName().equalsIgnoreCase("SPEED"))return effect;return null;}
 public static Material playerHeadMaterial(){try{return Material.valueOf("PLAYER_HEAD");}catch(IllegalArgumentException legacy){return Material.valueOf("SKULL_ITEM");}}
 public static void skullOwner(SkullMeta meta,OfflinePlayer player){
  try{Method modern=meta.getClass().getMethod("setOwningPlayer",OfflinePlayer.class);modern.invoke(meta,player);}
  catch(ReflectiveOperationException unavailable){invoke(meta,"setOwner",new Class<?>[]{String.class},player.getName());}
 }
 public static String inventoryTitle(Object view){Object value=noArgs(view,"getTitle");return value==null?"":String.valueOf(value);}
}
