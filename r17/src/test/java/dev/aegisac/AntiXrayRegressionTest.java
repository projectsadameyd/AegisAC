package dev.aegisac;

import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

public final class AntiXrayRegressionTest {
    private static int checks;
    private static void require(boolean b,String label){checks++;if(!b)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception {
        Path dir=Files.createTempDirectory("aegis-r17-xray-");Path file=dir.resolve("paper-world.yml");
        Files.writeString(file,"_version: 31\nentities:\n  spawning:\n    per-player-mob-spawns: false\nanticheat:\n  anti-xray:\n    enabled: false\n    hidden-blocks: [custom_ore]\n    replacement-blocks: [custom_stone]\n");byte[] original=Files.readAllBytes(file);
        require(AegisAntiXray.apply(file,false,320,3,true,true),"profile installed");
        YamlConfiguration y=VpnGate.readStrict(file.toFile());String key="anticheat.anti-xray";
        require(y.getBoolean(key+".enabled"),"anti-xray enabled");require(y.getInt(key+".engine-mode")==3,"mode3 configured");require(y.getInt(key+".max-block-height")==320,"all overworld heights covered");require(!y.getBoolean(key+".use-permission"),"no implicit operator bypass");require(y.getBoolean(key+".lava-obscures"),"lava obscures");require(y.getStringList(key+".hidden-blocks").contains("air"),"cave air decoys");require(y.getStringList(key+".hidden-blocks").contains("deepslate_diamond_ore"),"deep ores covered");require(y.getStringList(key+".hidden-blocks").contains("custom_ore"),"custom additions retained");require(y.getStringList(key+".replacement-blocks").contains("custom_stone"),"custom replacement retained");require(y.getStringList(key+".replacement-blocks").contains("chest"),"buried container replacement");require(!y.getStringList(key+".hidden-blocks").contains("chest"),"tile entities not fake palette");require(!y.getBoolean("entities.spawning.per-player-mob-spawns"),"unrelated configuration retained");require(Arrays.equals(original,Files.readAllBytes(dir.resolve("paper-world.before-aegis-r17.yml"))),"exact original backup");
        byte[] once=Files.readAllBytes(file);require(!AegisAntiXray.apply(file,false,320,3,true,true),"idempotent apply");require(Arrays.equals(once,Files.readAllBytes(file)),"idempotent bytes");
        require(AegisAntiXray.apply(file,false,320,3,false,true),"cave-air option changed");y=VpnGate.readStrict(file.toFile());require(!y.getStringList(key+".hidden-blocks").contains("air"),"cave-air removal honoured");require(Arrays.equals(original,Files.readAllBytes(dir.resolve("paper-world.before-aegis-r17.yml"))),"original backup not overwritten");
        Path nether=dir.resolve("nether.yml");AegisAntiXray.apply(nether,true,256,3,true,true);y=VpnGate.readStrict(nether.toFile());require(y.getStringList(key+".hidden-blocks").contains("ancient_debris"),"nether debris covered");require(y.getStringList(key+".replacement-blocks").contains("netherrack"),"nether substrate");
        Path bad=dir.resolve("bad.yml");Files.writeString(bad,"anticheat: [unterminated\n");byte[] broken=Files.readAllBytes(bad);boolean rejected=false;try{AegisAntiXray.apply(bad,false,320,3,true,true);}catch(java.io.IOException expected){rejected=true;}require(rejected,"invalid config rejected");require(Arrays.equals(broken,Files.readAllBytes(bad)),"invalid config untouched");
        boolean modeRejected=false;try{AegisAntiXray.apply(file,false,320,99,true,true);}catch(IllegalArgumentException expected){modeRejected=true;}require(modeRejected,"unsupported engine mode rejected");
        try(var paths=Files.walk(dir)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
        System.out.println("PASS: "+checks+" anti-Xray configuration, backup, migration and corruption assertions");
    }
}
