package dev.aegisac;
import java.io.*;import java.nio.file.*;import java.util.*;import org.bukkit.configuration.file.YamlConfiguration;
/** Deploy the native punishment profile once; retain original detector physics/tolerances. */
final class NativeConfig {
 static void install(AegisPlugin plugin){try{
  Path folder=plugin.getDataFolder().toPath();Files.createDirectories(folder);Path marker=folder.resolve("native-profile-r14.installed");
  if(!Files.exists(marker)){Path config=folder.resolve("punishments.yml");if(Files.exists(config))Files.copy(config,folder.resolve("punishments.before-r14.yml"),StandardCopyOption.REPLACE_EXISTING);
   try(InputStream in=plugin.getResource("native-punishments.yml")){if(in==null)throw new IOException("Native profile missing");Files.copy(in,config,StandardCopyOption.REPLACE_EXISTING);}Files.writeString(marker,"Native prevention and single-engine action profile R14. Original detector thresholds unchanged.\n");
  }
 migrateSimulation(folder);migrateTimerSafety(folder);
 }catch(IOException failure){throw new IllegalStateException("Cannot install native punishment profile",failure);}}
 static void migrateSimulation(Path folder)throws IOException{
  Path config=folder.resolve("punishments.yml");if(!Files.exists(config))return;
  YamlConfiguration yaml=new YamlConfiguration();
  try{yaml.load(config.toFile());}catch(org.bukkit.configuration.InvalidConfigurationException bad){throw new IOException("Invalid native punishment configuration; not overwritten",bad);}
  if(!yaml.getStringList("Punishments.Simulation.checks").contains("Simulation"))return;
  if(yaml.getStringList("Punishments.Simulation.commands").isEmpty())return;
  Path backup=folder.resolve("punishments.before-simulation-safety.yml");
  if(!Files.exists(backup))Files.copy(config,backup);
  yaml.set("Punishments.Simulation.commands",List.of());
  Path temporary=folder.resolve("punishments.simulation-safety.tmp");
  yaml.save(temporary.toFile());
  try{Files.move(temporary,config,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
  catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,config,StandardCopyOption.REPLACE_EXISTING);}
 }

 static void migrateTimerSafety(Path folder)throws IOException{
  Path config=folder.resolve("punishments.yml");if(!Files.exists(config))return;
  YamlConfiguration yaml=new YamlConfiguration();
  try{yaml.load(config.toFile());}catch(org.bukkit.configuration.InvalidConfigurationException bad){throw new IOException("Invalid native punishment configuration; not overwritten",bad);}
  var groups=yaml.getConfigurationSection("Punishments");if(groups==null)return;
  boolean changed=false;String quietGroup=null;
  for(String group:new ArrayList<>(groups.getKeys(false))){
   String base="Punishments."+group;
   List<String> checks=yaml.getStringList(base+".checks");
   List<String> commands=yaml.getStringList(base+".commands");
   if(commands.isEmpty()){
    if(checks.size()==1&&"TimerLimit".equalsIgnoreCase(checks.get(0)))quietGroup=group;
    continue;
   }
   boolean includes=false,excluded=false;
   for(String rule:checks){
    if(rule.startsWith("!")){if("TimerLimit".equalsIgnoreCase(rule.substring(1)))excluded=true;}
    else if("timerlimit".contains(rule.toLowerCase(Locale.ROOT)))includes=true;
   }
   if(includes&&!excluded){
    var safe=new ArrayList<>(checks);safe.add("!TimerLimit");
    yaml.set(base+".checks",safe);changed=true;
   }
  }
  // Keep the native limiter enabled: removing it from all groups disables the check.
  if(quietGroup==null){
   String name="AegisTimerLimitCorrections";int suffix=2;
   while(groups.contains(name))name="AegisTimerLimitCorrections"+suffix++;
   String base="Punishments."+name;
   yaml.set(base+".remove-violations-after",60);
   yaml.set(base+".checks",List.of("TimerLimit"));
   yaml.set(base+".commands",List.of());changed=true;
  }
  if(!changed)return;
  Path backup=folder.resolve("punishments.before-timer-safety.yml");
  if(!Files.exists(backup))Files.copy(config,backup);
  Path temporary=folder.resolve("punishments.timer-safety.tmp");
  yaml.save(temporary.toFile());
  try{Files.move(temporary,config,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
  catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,config,StandardCopyOption.REPLACE_EXISTING);}
 }

}
