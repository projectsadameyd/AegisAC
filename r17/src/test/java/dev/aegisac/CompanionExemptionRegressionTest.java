package dev.aegisac;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import sun.misc.Unsafe;

/** Admission, command authorization and durable configuration behavior. No game or server mocks grant admission. */
public final class CompanionExemptionRegressionTest {
    private static int checks;
    private static void require(boolean okay,String reason){checks++;if(!okay)throw new AssertionError(reason);}
    private static void invalid(YamlConfiguration yaml)throws Exception {
        try{CompanionExemptions.read(yaml);throw new AssertionError("Invalid exemption configuration accepted");}
        catch(IOException expected){checks++;}
    }
    public static void main(String[] args)throws Exception {
        YamlConfiguration yaml=new YamlConfiguration();
        require(CompanionExemptions.read(yaml).isEmpty(),"existing configurations gain no implicit exemptions");
        yaml.set("exempt-usernames",List.of("Redzuk","REDZUK","Adammm08144"));
        Set<String> names=CompanionExemptions.read(yaml);
        require(names.size()==2&&CompanionExemptions.contains(names,"rEdZuK"),"case-insensitive exact username matching");
        require(!CompanionExemptions.contains(names,"redzuk2")&&!CompanionExemptions.contains(names,null),"no prefix or null bypass");
        for(Object bad:List.of("*",List.of("*"),List.of("red zuk"),List.of("x"),List.of("12345678901234567"),List.of(123),List.of(true))) {yaml.set("exempt-usernames",bad);invalid(yaml);}
        Path directory=Files.createTempDirectory("aegis-companion-exemptions-");File file=directory.resolve("client-guard.yml").toFile();
        yaml=new YamlConfiguration();yaml.set("enabled",true);yaml.set("download-url","https://example.com/custom-companion.jar");yaml.set("handshake-timeout-seconds",12);yaml.set("block-screen-seconds",30);yaml.save(file);
        require(CompanionExemptions.update(file,"Redzuk",true),"new exemption persisted");
        require(CompanionExemptions.read(VpnGate.readStrict(file)).equals(Set.of("redzuk")),"fresh load sees saved username");
        byte[] saved=Files.readAllBytes(file.toPath());
        require(!CompanionExemptions.update(file,"REDZUK",true),"duplicate add is idempotent");
        require(Arrays.equals(saved,Files.readAllBytes(file.toPath())),"duplicate does not rewrite settings");
        try{CompanionExemptions.update(file,"*",true);throw new AssertionError("Wildcard exemption accepted");}catch(IllegalArgumentException expected){checks++;}
        require(Arrays.equals(saved,Files.readAllBytes(file.toPath())),"rejected name preserves saved settings");
        require(CompanionExemptions.update(file,"Adammm08144",true),"second username added");
        require(CompanionExemptions.update(file,"rEdZuK",false),"case-insensitive removal persisted");
        YamlConfiguration loaded=VpnGate.readStrict(file);
        require(CompanionExemptions.read(loaded).equals(Set.of("adammm08144")),"removal survives reload");
        require(loaded.getBoolean("enabled")&&loaded.getInt("handshake-timeout-seconds")==12&&loaded.getInt("block-screen-seconds")==30&&loaded.getString("download-url").equals("https://example.com/custom-companion.jar"),"unrelated admission settings preserved");
        require(!CompanionExemptions.update(file,"redzuk",false),"repeat removal is idempotent");
        try(var files=Files.list(directory)){require(files.count()==1,"no temporary files left after atomic save");}
        admissionTrace("REDZUK",Set.of("redzuk"),false,false,true);
        admissionTrace("redzuk2",Set.of("redzuk"),false,false,false);
        admissionTrace("redzuk",Set.of(),false,false,false);
        admissionTrace("redzuk",Set.of("redzuk"),true,false,false);
        admissionTrace("redzuk",Set.of("redzuk"),false,true,false);
        CompanionGate gate=allocate();List<String> messages=new ArrayList<>();
        CommandSender denied=(CommandSender)Proxy.newProxyInstance(CommandSender.class.getClassLoader(),new Class<?>[]{CommandSender.class},(proxy,method,values)->{
            if(method.getName().equals("hasPermission"))return false;
            if(method.getName().equals("sendMessage")){messages.add(String.valueOf(values[0]));return null;}
            throw new AssertionError("Unauthorized command touched "+method.getName());
        });
        require(gate.command(denied,new String[]{"client","exempt","add","redzuk"}),"companion command handled");
        require(messages.size()==1&&messages.get(0).contains("Permission denied"),"non-admin cannot mutate the exemption list");
        System.out.println("PASS "+checks+" companion exemption admission, authorization and persistence assertions");
    }
    private static void admissionTrace(String name,Set<String> exemptions,boolean startupError,boolean closed,boolean expectedExempt)throws Exception {
        CompanionGate gate=allocate();Class<?> settings=Class.forName("dev.aegisac.CompanionGate$Settings");
        Constructor<?> constructor=settings.getDeclaredConstructors()[0];constructor.setAccessible(true);
        set(gate,"settings",constructor.newInstance(true,"https://example.com/mod.jar",12,30,exemptions));
        AtomicLong exempted=new AtomicLong();set(gate,"exempted",exempted);set(gate,"busy",new AtomicLong());set(gate,"slots",new Semaphore(0));set(gate,"closed",closed);if(startupError)set(gate,"startupError","Invalid configuration");
        AtomicBoolean disconnected=new AtomicBoolean();
        PlayerProfile profile=(PlayerProfile)Proxy.newProxyInstance(PlayerProfile.class.getClassLoader(),new Class<?>[]{PlayerProfile.class},(proxy,method,values)->{
            if(method.getName().equals("getName"))return name;
            throw new AssertionError("Admission accessed unexpected profile API "+method.getName());
        });
        PlayerConfigurationConnection connection=(PlayerConfigurationConnection)Proxy.newProxyInstance(PlayerConfigurationConnection.class.getClassLoader(),new Class<?>[]{PlayerConfigurationConnection.class},(proxy,method,values)->{
            if(method.getName().equals("getProfile"))return profile;
            if(method.getName().equals("disconnect")){disconnected.set(true);return null;}
            throw new AssertionError("Exemption touched dialog, challenge or player state: "+method.getName());
        });
        gate.configure(new AsyncPlayerConnectionConfigureEvent(connection));
        require((exempted.get()==1)==expectedExempt,"exemption decision for "+name+" startupError="+startupError+" closed="+closed);
        require(disconnected.get()!=expectedExempt,"only the explicit valid exemption skips the full admission path");
    }
    private static CompanionGate allocate()throws Exception {Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);return (CompanionGate)((Unsafe)f.get(null)).allocateInstance(CompanionGate.class);}
    private static void set(Object target,String name,Object value)throws Exception {Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
}
