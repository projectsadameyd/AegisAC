package dev.aegisac;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

/** Applies only the anti-Xray subtree, with an exact backup, and requires a full server restart. */
final class AegisAntiXray {
    private static final String KEY="anticheat.anti-xray";
    private int changed,configured;
    private final List<String> errors=new ArrayList<>();
    int install(List<World> worlds,YamlConfiguration settings) {
        changed=0;configured=0;errors.clear();
        for(World world:worlds) {
            try {
                if(world.getEnvironment()==World.Environment.THE_END){continue;}
                Path folder=world.getWorldFolder().toPath().toRealPath();
                Path file=folder.resolve("paper-world.yml");
                if(Files.isSymbolicLink(file))throw new IOException("World config is a symbolic link");
                if(apply(file,world.getEnvironment()==World.Environment.NETHER,world.getMaxHeight(),settings.getInt("anti-xray.engine-mode",3),settings.getBoolean("anti-xray.hide-cave-air",true),settings.getBoolean("anti-xray.lava-obscures",true)))changed++;
                configured++;
            }catch(IOException|IllegalArgumentException failure){errors.add(world.getName()+": "+failure.getClass().getSimpleName());}
        }
        return changed;
    }
    static boolean apply(Path file,boolean nether,int height,int engine,boolean caveAir,boolean lava) throws IOException {
        if(engine!=2&&engine!=3)throw new IllegalArgumentException("anti-xray.engine-mode must be 2 or 3");
        if(height<16||height>4096)throw new IllegalArgumentException("Unsupported world height");
        YamlConfiguration yaml;
        if(Files.exists(file)) {
            if(Files.size(file)>1024*1024)throw new IOException("World config exceeds bounded size");
            yaml=VpnGate.readStrict(file.toFile());
        }else yaml=new YamlConfiguration();
        String before=yaml.saveToString();
        yaml.set(KEY+".enabled",true);yaml.set(KEY+".engine-mode",engine);
        yaml.set(KEY+".max-block-height",((height+15)/16)*16);yaml.set(KEY+".update-radius",2);
        yaml.set(KEY+".lava-obscures",lava);yaml.set(KEY+".use-permission",false);
        List<String> hidden=new ArrayList<>();if(caveAir)hidden.add("air");
        if(nether)hidden.addAll(List.of("ancient_debris","nether_gold_ore","nether_quartz_ore","bone_block","glowstone","magma_block","nether_bricks","polished_blackstone_bricks"));
        else hidden.addAll(List.of("copper_ore","deepslate_copper_ore","raw_copper_block","diamond_ore","deepslate_diamond_ore","emerald_ore","deepslate_emerald_ore","gold_ore","deepslate_gold_ore","iron_ore","deepslate_iron_ore","raw_iron_block","lapis_ore","deepslate_lapis_ore","redstone_ore","deepslate_redstone_ore","coal_ore","deepslate_coal_ore"));
        List<String> replacement=new ArrayList<>(nether?List.of("netherrack","basalt","blackstone","gravel","soul_sand","soul_soil"):List.of("stone","deepslate","andesite","diorite","granite","dirt","gravel","tuff","calcite","smooth_basalt","amethyst_block","budding_amethyst","oak_planks"));
        replacement.addAll(List.of("chest","trapped_chest","ender_chest","spawner","barrel"));
        // Preserve server-specific additions except invalid fake tile entities.
        Set<String> tileEntities=Set.of("chest","trapped_chest","ender_chest","spawner","barrel","shulker_box");
        for(String old:yaml.getStringList(KEY+".hidden-blocks"))if(!tileEntities.contains(old)&&!hidden.contains(old)&&(!old.equals("air")||caveAir))hidden.add(old);
        for(String old:yaml.getStringList(KEY+".replacement-blocks"))if(!replacement.contains(old))replacement.add(old);
        yaml.set(KEY+".hidden-blocks",hidden);yaml.set(KEY+".replacement-blocks",replacement);
        if(before.equals(yaml.saveToString()))return false;
        Files.createDirectories(file.getParent());Path backup=file.resolveSibling("paper-world.before-aegis-r17.yml");
        if(Files.exists(file)&&!Files.exists(backup))Files.copy(file,backup,StandardCopyOption.COPY_ATTRIBUTES);
        VpnGate.writeAtomic(yaml,file);return true;
    }
    String status(){return "worlds-configured-on-disk="+configured+" changed="+changed+" errors="+errors.size()+"; full restart required after changes";}
    List<String> errors(){return List.copyOf(errors);}
}
