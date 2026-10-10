package dev.aegisac;
import java.util.*;import java.util.concurrent.*;import java.lang.reflect.*;import java.nio.file.*;import java.util.logging.*;import java.net.*;
import org.bukkit.*;import org.bukkit.entity.Player;import org.bukkit.command.*;import org.bukkit.scheduler.*;import org.bukkit.plugin.java.JavaPlugin;
import ac.grim.grimac.checks.Check;import ac.grim.grimac.api.GrimUser;import ac.grim.grimac.api.event.events.CommandExecuteEvent;
/** Exercises actual native action routing, queued permissions, sanctions and persistence. */
public final class NativeActionRegressionTest {
 static int assertions;static void require(boolean ok,String why){assertions++;if(!ok)throw new AssertionError(why);}
 static final sun.misc.Unsafe U;static{try{Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);U=(sun.misc.Unsafe)f.get(null);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 static Field field(Class<?> type,String name)throws Exception{for(Class<?> c=type;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 static Object get(Object o,String key)throws Exception{return field(o.getClass(),key).get(o);}static void set(Object o,String key,Object value)throws Exception{field(o.getClass(),key).set(o,value);}
 static Object empty(Method m){Class<?> t=m.getReturnType();if(!t.isPrimitive())return null;if(t==boolean.class)return false;if(t==byte.class)return (byte)0;if(t==short.class)return (short)0;if(t==long.class)return 0L;if(t==double.class)return 0d;if(t==float.class)return 0f;if(t==char.class)return '\0';return t==void.class?null:0;}
 @SuppressWarnings("unchecked") static <T>T proxy(Class<T> t,InvocationHandler h){return (T)java.lang.reflect.Proxy.newProxyInstance(t.getClassLoader(),new Class[]{t},(p,m,a)->{if(m.getName().equals("hashCode"))return System.identityHashCode(p);if(m.getName().equals("equals"))return p==a[0];if(m.getName().equals("toString"))return t.getSimpleName()+"Fixture";return h.invoke(p,m,a);});}
 static Check check(String name)throws Exception{Check c=(Check)U.allocateInstance(Check.class);set(c,"checkName",name);set(c,"isEnabled",true);return c;}
 static AegisGrimAddon addon(Path path,Logger log)throws Exception{AegisPlugin owner=(AegisPlugin)U.allocateInstance(AegisPlugin.class);set(owner,"dataFolder",path.toFile());set(owner,"logger",log);set(owner,"classLoader",NativeActionRegressionTest.class.getClassLoader());Files.createDirectories(path);Constructor<AegisGrimAddon> ctor=AegisGrimAddon.class.getDeclaredConstructor(AegisPlugin.class);ctor.setAccessible(true);return ctor.newInstance(owner);}
 static void stop(AegisGrimAddon a)throws Exception{a.history().close();((ExecutorService)get(a,"async")).shutdownNow();}
 static void action(AegisGrimAddon a,GrimUser u,Check c,String tag)throws Exception{CommandExecuteEvent e=new CommandExecuteEvent(u,c,"native detail","expanded original text");NativeOutputBridge.tag(e,tag);Method m=AegisGrimAddon.class.getDeclaredMethod("onGrimCommand",CommandExecuteEvent.class);m.setAccessible(true);m.invoke(a,e);require(e.isCancelled(),"Wrapper action escaped native command output");}
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  UUID id=UUID.randomUUID();List<Runnable> queue=new ArrayList<>();List<String> kicks=new ArrayList<>(),messages=new ArrayList<>(),logs=new ArrayList<>();boolean[] online={true},exempt={false};
  Player player=proxy(Player.class,(p,m,a)->switch(m.getName()){case "getUniqueId"->id;case "getName"->"NativeFixture";case "isOnline"->online[0];case "hasPermission"->exempt[0];case "getAddress"->new InetSocketAddress("127.0.0.1",25565);case "getGameMode"->GameMode.SURVIVAL;case "kickPlayer"->{kicks.add((String)a[0]);yield null;}case "sendMessage"->{if(a[0] instanceof String s)messages.add(s);yield null;}default->empty(m);});
  BukkitScheduler scheduler=proxy(BukkitScheduler.class,(p,m,a)->{if(m.getName().equals("runTask")&&a[1] instanceof Runnable r){queue.add(r);return null;}return empty(m);});
  Server server=proxy(Server.class,(p,m,a)->switch(m.getName()){case "getScheduler"->scheduler;case "getPlayer"->online[0]?player:null;case "getPlayerExact"->online[0]?player:null;case "getOnlinePlayers"->List.of(player);case "getTPS"->new double[]{20,20,20};case "isPrimaryThread"->true;default->empty(m);});field(Bukkit.class,"server").set(null,server);
  Logger log=Logger.getLogger("R14NativeTest");log.setUseParentHandlers(false);log.addHandler(new Handler(){public void publish(LogRecord r){logs.add(r.getMessage());}public void close(){}public void flush(){}});
  Path folder=Files.createTempDirectory("native-r14-");Files.writeString(folder.resolve("aegis.yml"),"temp-ban-1-minutes: 7\ntemp-ban-2-minutes: 13\ntemp-ban-3-minutes: 23\n");AegisGrimAddon a=addon(folder,log);
  GrimUser user=proxy(GrimUser.class,(p,m,v)->switch(m.getName()){case "getUniqueId"->id;case "getName"->"NativeFixture";default->empty(m);});Check simulation=check("Simulation");
  action(a,user,simulation,"[aegis]");require(queue.isEmpty()&&kicks.isEmpty()&&logs.isEmpty(),"Simulation threshold still automatically punished");

  Check limiter=check("TimerLimit");
  for(int i=0;i<1000;i++)action(a,user,limiter,"[aegis]");
  require(queue.isEmpty()&&kicks.isEmpty()&&logs.isEmpty()&&a.history().size()==0,"TimerLimit burst created a warning, sanction or escalation");
  AegisGrimAddon realTimerOwner=addon(Files.createTempDirectory("timer-action-"),log);
  action(realTimerOwner,user,check("Timer"),"[aegis]");
  require(queue.size()==1,"Genuine native Timer action was disabled");queue.removeFirst().run();
  require(kicks.size()==1&&realTimerOwner.history().size()==2,"Genuine Timer action no longer bans");
  stop(realTimerOwner);kicks.clear();messages.clear();logs.clear();
  Path nativeFolder=Files.createTempDirectory("simulation-migration-");Files.writeString(nativeFolder.resolve("punishments.yml"),"Punishments:\n  Simulation:\n    checks: [Simulation]\n    commands: [\"15:0 [aegis]\"]\n  HardPackets:\n    commands: [\"1:0 [aegis]\"]\n");
  NativeConfig.migrateSimulation(nativeFolder);var y=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(nativeFolder.resolve("punishments.yml").toFile());require(y.getStringList("Punishments.Simulation.commands").isEmpty(),"Existing Simulation commands were not migrated");require(y.getStringList("Punishments.HardPackets.commands").equals(List.of("1:0 [aegis]")),"Packet protection profile changed");require(Files.exists(nativeFolder.resolve("punishments.before-simulation-safety.yml")),"Migration backup missing");String migrated=Files.readString(nativeFolder.resolve("punishments.yml"));NativeConfig.migrateSimulation(nativeFolder);require(Files.readString(nativeFolder.resolve("punishments.yml")).equals(migrated),"Repeat migration rewrote configuration");
  Check c=check("BadPacketsY");
  action(a,user,c,"[alert]");require(queue.isEmpty()&&logs.isEmpty()&&kicks.isEmpty(),"Raw native flag published/banned");
  action(a,user,c,"[aegis]");action(a,user,c,"[aegis]");require(queue.size()==1&&a.pendingReviews()==1,"Duplicate main-thread actions were not coalesced");require(kicks.isEmpty(),"Packet thread applied a Bukkit ban");queue.removeFirst().run();
  require(kicks.size()==1&&a.history().size()==2,"A native threshold required another engine or lost its ban");require(kicks.getFirst().startsWith("§6§lAegisAC")&&kicks.getFirst().contains("7m")&&kicks.getFirst().contains("@_adam814"),"Screen or legacy duration migration changed");require(logs.size()==1&&logs.getFirst().contains("Confirmed native detection"),"Raw/duplicate logs published");require(messages.stream().anyMatch(v->v.contains("Cheat Detected")),"Yellow warning missing");
  action(a,user,c,"[aegis]");exempt[0]=true;queue.removeFirst().run();require(kicks.size()==1,"Queued action ignored a new native permission");exempt[0]=false;
  NativeSession.manualExempt(id,System.currentTimeMillis()+60000);action(a,user,c,"[aegis]");require(queue.isEmpty(),"Manual exemption allowed automatic sanction");NativeSession.manualExempt(id,0);
  NativeSession.beginReset(id);action(a,user,c,"[aegis]");require(queue.isEmpty(),"Native counter reset replayed an automatic action");NativeSession.finishReset(id);
  set(c,"experimental",true);action(a,user,c,"[aegis]");require(queue.isEmpty(),"Experimental check automatically banned");set(c,"experimental",false);c.setEnabled(false);action(a,user,c,"[aegis]");require(queue.isEmpty(),"Disabled check automatically banned");c.setEnabled(true);
  action(a,user,c,"[aegis]");online[0]=false;queue.removeFirst().run();require(kicks.size()==1,"Offline player acquired a delayed ban");online[0]=true;
  field(AegisGrimAddon.class,"INSTANCE").set(null,a);AegisGrimAddon.acceptDonut(player,"world.nuker",500,"plugin bulk mining",null);AegisGrimAddon.acceptDonut(player,"movement.fly",500,"Bukkit heuristic",null);require(queue.isEmpty()&&kicks.size()==1,"Donut bulk/movement heuristic caused false automatic ban");field(AegisGrimAddon.class,"INSTANCE").set(null,null);
  CommandSender admin=proxy(CommandSender.class,(p,m,v)->m.getName().equals("hasPermission")?true:empty(m));CommandSender denied=proxy(CommandSender.class,(p,m,v)->empty(m));
  a.onCommand(denied,null,"aegis",new String[]{"ban",id.toString()});require(((Map<?,?>)get(a,"tempBanCount")).get(id).equals(1),"Unauthorized sender banned");
  for(int i=2;i<=4;i++)a.onCommand(admin,null,"aegis",new String[]{"ban",id.toString(),"QA staff decision"});require(((Set<?>)get(a,"permanentUuids")).contains(id)&&((Set<?>)get(a,"permanentIps")).contains("127.0.0.1"),"Escalation failed");
  Player changed=proxy(Player.class,(p,m,v)->switch(m.getName()){case "getUniqueId"->id;case "getName"->"ChangedName";default->empty(m);});
  var login=new org.bukkit.event.player.PlayerLoginEvent(changed,"localhost",InetAddress.getByName("127.0.0.2"));a.login(login);require(login.getResult()==org.bukkit.event.player.PlayerLoginEvent.Result.KICK_BANNED,"Permanent UUID login allowed");require(((Map<?,?>)get(a,"knownIps")).get(id).equals("127.0.0.1")&&((Map<?,?>)get(a,"lastName")).get(id).equals("NativeFixture"),"Rejected login overwrote sanction identity");
  AegisGrimAddon loaded=addon(folder,log);require(((Set<?>)get(loaded,"permanentUuids")).contains(id),"Restart lost sanctions");stop(loaded);
  a.onCommand(admin,null,"aegis",new String[]{"unban",id.toString()});require(!((Set<?>)get(a,"permanentUuids")).contains(id)&&!((Set<?>)get(a,"permanentIps")).contains("127.0.0.1")&&a.activeBans().isEmpty(),"Unban left active sanctions");require(((Map<?,?>)get(a,"tempBanCount")).get(id).equals(3),"Unban erased escalation history");
  require(AegisGrimAddon.validDiscordWebhook("https://discord.com/api/webhooks/12345/abc_123"),"Valid webhook rejected");for(String u:List.of("http://discord.com/api/webhooks/1/a","https://discord.com@localhost/api/webhooks/1/a","https://discord.com.evil.example/api/webhooks/1/a"))require(!AegisGrimAddon.validDiscordWebhook(u),"Unsafe webhook accepted");
  Method json=AegisGrimAddon.class.getDeclaredMethod("json",String.class);json.setAccessible(true);require(json.invoke(null,"line\n\"\\\r\u0001").equals("line\\n\\\"\\\\\\r\\u0001"),"Webhook JSON escaping changed");
  stop(a);System.out.println("PASS: "+assertions+" native action, permissions, quiet output, sanctions, persistence and migration assertions");
 }
}
