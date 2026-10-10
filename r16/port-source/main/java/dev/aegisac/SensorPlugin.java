package dev.aegisac;
import org.bukkit.*;
import org.bukkit.plugin.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.*;
import org.bukkit.generator.*;
import java.io.*;
import java.util.*;
import java.util.logging.Logger;
/** Donut's lifecycle adapter. Delegates Bukkit ownership to the one installed Aegis plugin. */
public abstract class SensorPlugin implements Plugin {
 private final AegisPlugin owner=AegisPlugin.instance();private FileConfiguration config;
 public SensorPlugin(){if(owner==null)throw new IllegalStateException("Aegis lifecycle is unavailable");}
 public File getDataFolder(){return owner.getDataFolder();}
 public PluginDescriptionFile getDescription(){return owner.getDescription();}
 public io.papermc.paper.plugin.configuration.PluginMeta getPluginMeta(){return owner.getPluginMeta();}
 public FileConfiguration getConfig(){if(config==null)reloadConfig();return config;}
 public InputStream getResource(String name){return owner.getResource(name.equals("config.yml")?"donut-default.yml":name);}
 public void saveConfig(){try{getConfig().save(new File(getDataFolder(),"donut.yml"));}catch(IOException e){throw new IllegalStateException(e);}}
 public void saveDefaultConfig(){File file=new File(getDataFolder(),"donut.yml");if(!file.exists())try(InputStream in=getResource("config.yml")){if(in==null)throw new IOException("Assistant defaults missing");java.nio.file.Files.copy(in,file.toPath());}catch(IOException e){throw new IllegalStateException(e);}reloadConfig();}
 public void saveResource(String name,boolean replace){owner.saveResource(name,replace);}
 public void reloadConfig(){config=YamlConfiguration.loadConfiguration(new File(getDataFolder(),"donut.yml"));}
 public PluginLoader getPluginLoader(){return owner.getPluginLoader();}
 public Server getServer(){return owner.getServer();}public boolean isEnabled(){return owner.isEnabled();}
 public void onDisable(){}public void onLoad(){}public void onEnable(){}
 public boolean isNaggable(){return owner.isNaggable();}public void setNaggable(boolean value){owner.setNaggable(value);}
 public ChunkGenerator getDefaultWorldGenerator(String world,String id){return null;}
 public BiomeProvider getDefaultBiomeProvider(String world,String id){return null;}
 public Logger getLogger(){return owner.getLogger();}public String getName(){return owner.getName();}
 public io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager<Plugin> getLifecycleManager(){return owner.getLifecycleManager();}
 public String namespace(){return owner.namespace();}
 public PluginCommand getCommand(String name){return null;}
 public boolean onCommand(CommandSender sender,Command command,String label,String[] args){return false;}
 public List<String> onTabComplete(CommandSender sender,Command command,String label,String[] args){return List.of();}
}
