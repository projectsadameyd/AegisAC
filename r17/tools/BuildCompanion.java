import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import javax.tools.ToolProvider;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.*;
import net.fabricmc.tinyremapper.*;

/** Directory-output build for hosts whose JDK ZIP filesystem incorrectly reports read-only. */
public final class BuildCompanion {
    private static final Path ROOT=Path.of("").toAbsolutePath(),R17=ROOT.resolve("r17"),INPUTS=R17.resolve("inputs"),BUILD=R17.resolve("build/companion-direct");
    private static MemoryMappingTree mojang,intermediary;
    public static void main(String[] args)throws Exception {
        Files.createDirectories(BUILD);Path tiny=BUILD.resolve("intermediary.tiny");
        try(var zip=new ZipFile(INPUTS.resolve("fabric-intermediary-1.21.11.jar").toFile())){Files.write(tiny,zip.getInputStream(zip.getEntry("mappings/mappings.tiny")).readAllBytes());}
        mojang=new MemoryMappingTree();MappingReader.read(INPUTS.resolve("mojang-client-1.21.11.txt"),mojang);
        intermediary=new MemoryMappingTree();MappingReader.read(tiny,intermediary);
        Path mc=BUILD.resolve("minecraft-intermediary");
        if(!Files.exists(mc.resolve(".complete"))) {
            TinyRemapper remapper=TinyRemapper.newRemapper().threads(2).withMappings(TinyUtils.createTinyMappingProvider(tiny,"official","intermediary")).build();
            try{remapper.readInputs(INPUTS.resolve("minecraft-client-1.21.11.jar"));remapper.apply((name,bytes)->{try{Path file=mc.resolve(name+".class");Files.createDirectories(file.getParent());Files.write(file,bytes);}catch(IOException e){throw new UncheckedIOException(e);}});}
            finally{remapper.finish();}Files.writeString(mc.resolve(".complete"),"1.21.11\n");System.out.println("Mapped Minecraft compiler interfaces to Fabric intermediary names.");
        }
        String source=Files.readString(R17.resolve("companion/src/client/java/dev/aegisac/companion/AegisCompanionClient.java"));
        var payload=type("net/minecraft/network/protocol/common/custom/CustomPacketPayload");
        var id=type("net/minecraft/resources/Identifier");var codec=type("net/minecraft/network/codec/StreamCodec");
        String codecFactory=method(codec,"of",2),parse=method(id,"parse",1),payloadType=method(payload,"type",0);
        Map<String,String> classes=new LinkedHashMap<>();for(String name:List.of("net/minecraft/network/FriendlyByteBuf","net/minecraft/network/codec/StreamCodec","net/minecraft/network/protocol/common/custom/CustomPacketPayload","net/minecraft/resources/Identifier")){String mapped=mappedClass(type(name));classes.put(name.substring(name.lastIndexOf('/')+1),mapped.substring(mapped.lastIndexOf('/')+1));source=source.replace(name.replace('/','.'),mapped.replace('/','.'));}
        for(var e:classes.entrySet())source=source.replaceAll("\\b"+e.getKey()+"\\b",e.getValue());
        String nested=mappedClass(type("net/minecraft/network/protocol/common/custom/CustomPacketPayload$Type"));nested=nested.substring(nested.lastIndexOf('$')+1);
        source=source.replaceAll("\\bType\\b",nested).replace(classes.get("StreamCodec")+".of(",classes.get("StreamCodec")+"."+codecFactory+"(")
            .replace(classes.get("Identifier")+".parse(",classes.get("Identifier")+"."+parse+"(").replace(" type(){"," "+payloadType+"(){");
        Path generated=BUILD.resolve("generated/dev/aegisac/companion/AegisCompanionClient.java");Files.createDirectories(generated.getParent());Files.writeString(generated,source);
        List<Path> sources=new ArrayList<>();try(var stream=Files.walk(R17.resolve("companion/shared"))){sources.addAll(stream.filter(p->p.toString().endsWith(".java")).toList());}sources.add(generated);
        List<Path> cp=new ArrayList<>(List.of(mc,INPUTS.resolve("fabric-loader-0.19.5.jar"),INPUTS.resolve("fabric-api-base-1.0.5+4ebb5c083e.jar"),INPUTS.resolve("fabric-networking-api-v1-5.1.6+6b6d71a53e.jar")));
        Path testLibraries=R17.resolve("server-test/libraries");if(Files.exists(testLibraries))try(var stream=Files.walk(testLibraries)){cp.addAll(stream.filter(p->p.toString().endsWith(".jar")).toList());}
        Path libraries=INPUTS.resolve("companion-libraries");if(Files.exists(libraries))try(var stream=Files.walk(libraries)){cp.addAll(stream.filter(p->p.toString().endsWith(".jar")).toList());}
        Path classesOut=BUILD.resolve("classes");Files.createDirectories(classesOut);
        List<String> javac=new ArrayList<>(List.of("--release","21","-encoding","UTF-8","-proc:none","-cp",String.join(File.pathSeparator,cp.stream().map(Path::toString).toList()),"-d",classesOut.toString()));sources.forEach(p->javac.add(p.toString()));
        if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,javac.toArray(String[]::new))!=0)throw new IOException("Companion compilation failed");
        Map<String,byte[]> entries=new TreeMap<>();try(var paths=Files.walk(classesOut)){for(Path p:paths.filter(Files::isRegularFile).toList())entries.put(classesOut.relativize(p).toString().replace('\\','/'),Files.readAllBytes(p));}
        Path resources=R17.resolve("companion/src/main/resources");try(var paths=Files.walk(resources)){for(Path p:paths.filter(Files::isRegularFile).toList())entries.put(resources.relativize(p).toString().replace('\\','/'),Files.readAllBytes(p));}
        entries.put("fabric.mod.json",new String(entries.get("fabric.mod.json"),StandardCharsets.UTF_8).replace("${version}","0.17.0-preview2.hotfix2").getBytes(StandardCharsets.UTF_8));
        entries.put("LICENSE-AegisAC-Companion.txt",Files.readAllBytes(R17.resolve("LICENSE-COMPANION.txt")));
        Path jar=R17.resolve("build/AegisAC-Companion-R17-preview2-hotfix2-1.21.11.jar");try(var out=new ZipOutputStream(Files.newOutputStream(jar))){for(var entry:entries.entrySet()){ZipEntry e=new ZipEntry(entry.getKey());e.setTime(315532800000L);out.putNextEntry(e);out.write(entry.getValue());out.closeEntry();}}
        String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));Files.writeString(BUILD.resolve("SHA256SUMS.txt"),sha+"  "+jar.getFileName()+"\n");
        System.out.println("BUILT "+jar+" bytes="+Files.size(jar)+" sha256="+sha);
    }
    private static MappingTreeView.ClassMappingView type(String name){var c=mojang.getClass(name);if(c==null)throw new IllegalStateException("Missing Mojang mapping: "+name);return c;}
    private static String mappedClass(MappingTreeView.ClassMappingView named){var c=intermediary.getClass(named.getDstName(0));if(c==null)throw new IllegalStateException("Missing intermediary class: "+named.getSrcName());return c.getDstName(0);}
    private static String method(MappingTreeView.ClassMappingView owner,String name,int parameters){
        var methods=owner.getMethods().stream().filter(m->m.getSrcName().equals(name)).toList();
        if(methods.isEmpty())throw new IllegalStateException("Missing method: "+name);
        // The codec factory and Identifier parser each have a unique two/one-argument signature here.
        for(var m:methods){String desc=m.getSrcDesc();if(org.objectweb.asm.Type.getArgumentTypes(desc).length!=parameters)continue;
            var target=intermediary.getClass(owner.getDstName(0)).getMethod(m.getDstName(0),m.getDstDesc(0));if(target!=null)return target.getDstName(0);
        }throw new IllegalStateException("Missing intermediary method: "+owner.getSrcName()+"."+name);
    }
}
