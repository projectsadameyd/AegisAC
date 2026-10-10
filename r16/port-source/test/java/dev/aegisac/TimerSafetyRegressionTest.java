
package dev.aegisac;
import java.nio.file.*;import java.util.*;import java.io.*;
import org.bukkit.configuration.ConfigurationSection;import org.bukkit.configuration.file.YamlConfiguration;
import ac.grim.grimac.player.GrimPlayer;import ac.grim.grimac.manager.CheckManager;import ac.grim.grimac.manager.PunishmentManager;
import ac.grim.grimac.checks.Check;import ac.grim.grimac.api.config.ConfigManager;
/** Exercises the real native group matcher and ledger, not a reimplementation. */
public final class TimerSafetyRegressionTest {
 static final class FixturePlayer extends GrimPlayer { java.util.Collection<? extends ac.grim.grimac.api.AbstractCheck> fixtureChecks;private FixturePlayer(){super(null);}@Override public java.util.Collection<? extends ac.grim.grimac.api.AbstractCheck> getChecks(){return fixtureChecks;} }
 static int n;static void require(boolean ok,String why){n++;if(!ok)throw new AssertionError(why);}
 static Object raw(Object value){
  if(value instanceof ConfigurationSection section){var out=new LinkedHashMap<String,Object>();for(String k:section.getKeys(false))out.put(k,raw(section.get(k)));return out;}
  if(value instanceof List<?> list)return list.stream().map(TimerSafetyRegressionTest::raw).toList();
  return value;
 }
 static ConfigManager config(YamlConfiguration yaml){
  var values=(Map<?,?>)raw(yaml.getConfigurationSection("Punishments"));var groups=new ArrayList<>(values.values());
  return NativeActionRegressionTest.proxy(ConfigManager.class,(p,m,a)->{
   if(m.getName().equals("getStringListElse")&&a[0].equals("Punishments"))return groups;
   if(a!=null&&a.length==2)return a[1];
   return NativeActionRegressionTest.empty(m);
  });
 }
 static int ledger(Object group)throws Exception{
  Object ledger=NativeActionRegressionTest.get(group,"violations");var size=ledger.getClass().getDeclaredMethod("size");size.setAccessible(true);return (int)size.invoke(ledger);
 }
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  Path folder=Files.createTempDirectory("timer-safety-");
  String original=Files.readString(Path.of(args[0]));Path path=folder.resolve("punishments.yml");Files.writeString(path,original);
  var before=YamlConfiguration.loadConfiguration(path.toFile());
  NativeConfig.migrateTimerSafety(folder);var after=YamlConfiguration.loadConfiguration(path.toFile());
  require(after.getStringList("Punishments.Timer.checks").contains("!TimerLimit"),"Broad Timer group still counts TimerLimit");
  require(after.getStringList("Punishments.Timer.commands").equals(before.getStringList("Punishments.Timer.commands")),"Genuine Timer threshold changed");
  require(after.getStringList("Punishments.AegisTimerLimitCorrections.checks").equals(List.of("TimerLimit")),"Limiter no longer has an enabled group");
  require(after.getStringList("Punishments.AegisTimerLimitCorrections.commands").isEmpty(),"Limiter group has sanctions");
  for(String name:before.getConfigurationSection("Punishments").getKeys(false)){
   if(!name.equals("Timer"))require(raw(before.getConfigurationSection("Punishments."+name)).equals(raw(after.getConfigurationSection("Punishments."+name))),"Unrelated group changed: "+name);
  }
  Path backup=folder.resolve("punishments.before-timer-safety.yml");require(Files.readString(backup).equals(original),"Backup did not preserve the original");
  String migrated=Files.readString(path);NativeConfig.migrateTimerSafety(folder);require(Files.readString(path).equals(migrated),"Migration is not idempotent");require(Files.readString(backup).equals(original),"Repeat migration overwrote backup");
  Check timer=NativeActionRegressionTest.check("Timer"),limit=NativeActionRegressionTest.check("TimerLimit"),reach=NativeActionRegressionTest.check("Reach"),ground=NativeActionRegressionTest.check("GroundSpoof"),packet=NativeActionRegressionTest.check("BadPacketsY");
  var checks=List.of(timer,limit,reach,ground,packet);for(Check c:checks)NativeActionRegressionTest.set(c,"alternativeName",c.getCheckName());
  FixturePlayer player=(FixturePlayer)NativeActionRegressionTest.U.allocateInstance(FixturePlayer.class);player.fixtureChecks=checks;
  PunishmentManager manager=new PunishmentManager(player);manager.reload(config(after));
  require(checks.stream().allMatch(Check::isEnabled),"Native detector was disabled by migration");
  var groups=(List<?>)NativeActionRegressionTest.get(manager,"groups");Object timerGroup=null,quiet=null;
  for(Object g:groups){var members=(List<?>)NativeActionRegressionTest.get(g,"checks");var commands=(List<?>)NativeActionRegressionTest.get(g,"commands");if(members.contains(timer)&&!commands.isEmpty())timerGroup=g;if(members.contains(limit)){require(commands.isEmpty(),"Limiter participates in a native sanction group");quiet=g;}}
  require(timerGroup!=null&&quiet!=null,"Native groups did not load");
  for(int i=0;i<2000;i++)manager.handleViolation(limit);
  require(ledger(quiet)==2000,"Native limiter evidence stopped being recorded");
  for(Object g:groups)if(!((List<?>)NativeActionRegressionTest.get(g,"commands")).isEmpty())require(ledger(g)==0,"Latency flags polluted a sanction counter");
  for(int i=0;i<19;i++)manager.handleViolation(timer);require(ledger(timerGroup)==19,"Real timer flags lost");
  manager.handleViolation(timer);require(ledger(timerGroup)==20,"Real timer threshold changed by latency isolation");
  manager.handleViolation(reach);Object reachGroup=groups.stream().filter(g->{try{return ((List<?>)NativeActionRegressionTest.get(g,"checks")).contains(reach);}catch(Exception e){throw new RuntimeException(e);}}).findFirst().orElseThrow();require(ledger(reachGroup)==1,"Reach recording changed");
  Path custom=Files.createTempDirectory("timer-custom-");
  Files.writeString(custom.resolve("punishments.yml"),"custom-key: retained\nPunishments:\n  Mixed:\n    checks: [timer, Reach]\n    commands: ['77:11 ban %player% custom']\n  AegisTimerLimitCorrections:\n    checks: [Knockback]\n    commands: ['5:0 [aegis]']\n");
  NativeConfig.migrateTimerSafety(custom);var cy=YamlConfiguration.loadConfiguration(custom.resolve("punishments.yml").toFile());
  require(cy.getString("custom-key").equals("retained"),"Custom key removed");require(cy.getStringList("Punishments.Mixed.checks").equals(List.of("timer","Reach","!TimerLimit")),"Mixed custom selector not protected");
  require(cy.getStringList("Punishments.Mixed.commands").equals(List.of("77:11 ban %player% custom")),"Custom commands changed");
  require(cy.getStringList("Punishments.AegisTimerLimitCorrections.checks").equals(List.of("Knockback")),"Existing name collision destroyed another check");
  require(cy.getStringList("Punishments.AegisTimerLimitCorrections2.checks").equals(List.of("TimerLimit")),"Name collision did not retain corrections");
  Path invalid=Files.createTempDirectory("timer-invalid-");Path bad=invalid.resolve("punishments.yml");Files.writeString(bad,"Punishments: [broken");String text=Files.readString(bad);boolean failed=false;try{NativeConfig.migrateTimerSafety(invalid);}catch(IOException expected){failed=true;}
  require(failed&&Files.readString(bad).equals(text)&&!Files.exists(invalid.resolve("punishments.before-timer-safety.yml")),"Invalid config was overwritten");
  Path fresh=Files.createTempDirectory("timer-fresh-");Files.writeString(fresh.resolve("punishments.yml"),Files.readString(Path.of(args[1])));String freshText=Files.readString(fresh.resolve("punishments.yml"));NativeConfig.migrateTimerSafety(fresh);require(Files.readString(fresh.resolve("punishments.yml")).equals(freshText),"Fresh safe profile unnecessarily rewritten");

  Path upgraded=Files.createTempDirectory("timer-upgrade-");Files.writeString(upgraded.resolve("native-profile-r14.installed"),"existing");
  Files.writeString(upgraded.resolve("punishments.yml"),original);
  AegisPlugin owner=(AegisPlugin)NativeActionRegressionTest.U.allocateInstance(AegisPlugin.class);
  NativeActionRegressionTest.set(owner,"dataFolder",upgraded.toFile());NativeActionRegressionTest.set(owner,"classLoader",TimerSafetyRegressionTest.class.getClassLoader());
  NativeActionRegressionTest.set(owner,"logger",java.util.logging.Logger.getLogger("TimerSafetyFixture"));
  NativeConfig.install(owner);
  var upgradedYaml=YamlConfiguration.loadConfiguration(upgraded.resolve("punishments.yml").toFile());
  require(upgradedYaml.getStringList("Punishments.Timer.checks").contains("!TimerLimit"),"Startup skipped safety migration on an existing server");
  require(Files.readString(upgraded.resolve("punishments.before-timer-safety.yml")).equals(original),"Startup upgrade lost existing profile backup");
  Path newInstall=Files.createTempDirectory("timer-first-install-");NativeActionRegressionTest.set(owner,"dataFolder",newInstall.toFile());NativeConfig.install(owner);
  var first=YamlConfiguration.loadConfiguration(newInstall.resolve("punishments.yml").toFile());
  require(first.getStringList("Punishments.Timer.checks").contains("!TimerLimit")&&first.getStringList("Punishments.AegisTimerLimitCorrections.commands").isEmpty(),"Fresh startup installed an unsafe profile");
  System.out.println("PASS: "+n+" timer migration, native group matcher and 2,000-latency-flag ledger assertions");
 }
}
