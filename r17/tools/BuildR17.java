import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.zip.*;
import javax.tools.ToolProvider;

/** Run from the repository: java r17/tools/BuildR17.java. JDK 21+; no Maven or Python needed. */
public final class BuildR17 {
    private static final String BASE_NAME="AegisAC-R16-VPNGuard-1.21.11.jar";
    private static final String SOURCE_NAME="AegisAC-R16-VPNGuard-Source.zip";
    private static final String ASSETS="https://github.com/projectsadameyd/AegisAC/releases/download/v4.2.0-r16-vpnguard1/";
    private static final Map<String,String> PINS=Map.of(BASE_NAME,"d9eb4b0ab5589e4ab6b51db1f5a34a67df603f015041bbbb36c5fb6d8b1cab1f",SOURCE_NAME,"65a974c43ba4159d6acb389f65177b6c8489fe17639d096199e98f16f8641691","paper-api.jar","c577b181c11a8674310e56c92a91e31c010b7f04c9bd10b91c3be18374401070","paper-api-dependencies.jar","4e9088779c2b5829367536f68d950c10715f6c75f277b242cc1bfcda43aa6648","linkage-api-dependencies.jar","64457a6979ee7d7f0e8772b3c1b550c0e69617886722e1f4f72db707d04b10ed");
    private static Path root,out,inputs;
    public static void main(String[] args)throws Exception {
        Path repo=Path.of("").toAbsolutePath();if(!Files.exists(repo.resolve("r16/port-source")))throw new IllegalStateException("Run from the repository root");
        root=repo.resolve("r17");out=root.resolve("build");inputs=root.resolve("inputs");Files.createDirectories(out);Files.createDirectories(inputs);
        for(String name:List.of(BASE_NAME,SOURCE_NAME)) {
            Path p=inputs.resolve(name);if(!Files.exists(p))download(ASSETS+name,p);verify(p,PINS.get(name));
        }
        try(ZipFile source=new ZipFile(inputs.resolve(SOURCE_NAME).toFile())) {
            for(String name:List.of("source/lib/paper-api.jar","source/lib/paper-api-dependencies.jar","original/lib/linkage-api-dependencies.jar")) {
                Path p=inputs.resolve(Path.of(name).getFileName());
                if(!Files.exists(p))try(InputStream in=source.getInputStream(source.getEntry("AegisAC-Multi-Version-Source/"+name))){Files.copy(in,p);}
                verify(p,PINS.get(p.getFileName().toString()));
            }
        }
        Path generated=out.resolve("generated/dev/aegisac");Files.createDirectories(generated);
        String addon=read(repo.resolve("r16/port-source/main/java/dev/aegisac/AegisGrimAddon.java"));
        addon=replace(addon,"R16-VPNGUARD1","R17-NATIVE-GUARD1");
        addon=replace(addon,"               && !\"TimerLimit\".equalsIgnoreCase(var3.getCheckName())","               && !\"TimerLimit\".equalsIgnoreCase(var3.getCheckName())\n               && !R17SanctionSafety.timingOnly(var3.getCheckName())");
        addon=replace(addon,"   private void nativeAction(UUID var1, String var2, String var3, String var4, String var5, Check var6) {","   private void nativeAction(UUID var1, String var2, String var3, String var4, String var5, Check var6) {\n      if (R17SanctionSafety.timingOnly(var4)) return;");
        addon=replace(addon,"      if (var4.length > 0 && this.admin.handle(var1, var4)) {","      if (var4.length > 0 && this.plugin.guard() != null && this.plugin.guard().command(var1, var4)) return true;\n\n      if (var4.length > 0 && this.admin.handle(var1, var4)) {");
        addon=replace(addon,"               this.plugin.reloadConnectionGuard();","               this.plugin.reloadConnectionGuard();\n               if (this.plugin.guard() != null) this.plugin.guard().reload();");
        addon=replace(addon,"   String vpnStatus() {","   static boolean remoteActionSanctionsPaused() {\n      AegisGrimAddon owner = INSTANCE;\n      return owner == null || owner.reviewPaused(System.currentTimeMillis());\n   }\n\n   static void remoteInteractionKicked(Player player, String reason) {\n      AegisGrimAddon owner = INSTANCE;\n      if (owner == null) return;\n      owner.history.append(player.getUniqueId(), player.getName(), \"REMOTE_INTERACTION_KICK\", reason);\n      owner.plugin.getLogger().warning(\"AegisAC blocked repeated remote interactions: \" + player.getName() + \" \" + reason);\n      owner.postWebhook(\"Remote Interaction Blocked\", \"**Player:** \" + player.getName() + \"\\n**Evidence:** \" + reason + \"\\n**Action:** Kicked; no ban stage added.\", 16766720);\n   }\n\n   String vpnStatus() {");
        Files.writeString(generated.resolve("AegisGrimAddon.java"),addon);
        String admin=read(repo.resolve("r16/port-source/main/java/dev/aegisac/AegisAdminCommands.java"));
        admin=replace(admin,"      \"vpn\"\n   );","      \"vpn\",\n      \"guard\",\n      \"xray\"\n   );");
        Files.writeString(generated.resolve("AegisAdminCommands.java"),admin);
        List<Path> sourceFiles=files(root.resolve("src/main/java"),".java");sourceFiles.add(generated.resolve("AegisGrimAddon.java"));sourceFiles.add(generated.resolve("AegisAdminCommands.java"));
        Path classes=out.resolve("classes"),tests=out.resolve("tests"),temp=out.resolve("temp");Files.createDirectories(classes);Files.createDirectories(tests);Files.createDirectories(temp);
        String cp=classpath(List.of(inputs.resolve(BASE_NAME),inputs.resolve("paper-api.jar"),inputs.resolve("paper-api-dependencies.jar"),inputs.resolve("linkage-api-dependencies.jar")));
        compile(sourceFiles,classes,cp,17);
        Map<String,byte[]> overlay=new TreeMap<>();
        for(Path p:files(classes,".class"))overlay.put(classes.relativize(p).toString().replace('\\','/'),Files.readAllBytes(p));
        Path resources=root.resolve("src/main/resources");for(Path p:files(resources,null))overlay.put(resources.relativize(p).toString().replace('\\','/'),Files.readAllBytes(p));
        String plugin=read(repo.resolve("r16/port-source/main/resources/plugin.yml")).replaceFirst("(?m)^version:.*$","version: 4.3.0-r17-nativeguard-preview1-mc1.21.11");
        plugin+="\n  aegisac.guard.bypass:\n    description: Explicit bypass for the R17 remote action guard\n    default: false\n";
        overlay.put("plugin.yml",plugin.getBytes(StandardCharsets.UTF_8));
        overlay.put("aegis-compatibility.json",("{\"minecraft_version\":\"1.21.11\",\"runtime_java_minimum\":21,\"aegis_build\":\"R17-NATIVE-GUARD1-preview1\",\"base_release\":\"v4.2.0-r16-vpnguard1\",\"client_filesystem_scan\":false,\"passive_freecam_detection\":false,\"remote_interaction_prevention\":true,\"anti_xray\":\"per-world configuration; restart required\",\"validation\":\"see QA_REPORT.md\"}\n").getBytes(StandardCharsets.UTF_8));
        if(Files.exists(root.resolve("THIRD_PARTY_NOTICES.md")))overlay.put("R17-THIRD_PARTY_NOTICES.md",Files.readAllBytes(root.resolve("THIRD_PARTY_NOTICES.md")));
        Path jar=out.resolve("AegisAC-R17-NativeGuard-preview1-1.21.11.jar");
        try(ZipFile base=new ZipFile(inputs.resolve(BASE_NAME).toFile());ZipOutputStream z=new ZipOutputStream(Files.newOutputStream(jar))) {
            TreeSet<String> names=new TreeSet<>(overlay.keySet());base.stream().filter(e->!e.isDirectory()).forEach(e->names.add(e.getName()));
            for(String name:names) {
                byte[] data=overlay.get(name);if(data==null)try(InputStream in=base.getInputStream(base.getEntry(name))){data=in.readAllBytes();}
                ZipEntry entry=new ZipEntry(name);entry.setTime(315532800000L);z.putNextEntry(entry);z.write(data);z.closeEntry();
            }
        }
        int unchanged=0;
        try(ZipFile baseline=new ZipFile(inputs.resolve(BASE_NAME).toFile());ZipFile built=new ZipFile(jar.toFile())) {
            for(ZipEntry e:Collections.list(baseline.entries()))if(e.getName().startsWith("ac/grim/")&&e.getName().endsWith(".class")) {
                try(InputStream a=baseline.getInputStream(e);InputStream b=built.getInputStream(built.getEntry(e.getName()))){if(!Arrays.equals(a.readAllBytes(),b.readAllBytes()))throw new AssertionError("R16 engine bytecode changed: "+e.getName());}unchanged++;
            }
            if(built.stream().anyMatch(e->e.getName().startsWith("net/donutac/anticheat/command/")))throw new AssertionError("Unexpected upstream command classes");
        }
        List<Path> testFiles=files(repo.resolve("r16/port-source/test/java"),".java");
        Path oldActionTest=repo.resolve("r16/port-source/test/java/dev/aegisac/NativeActionRegressionTest.java");
        String actionTest=read(oldActionTest);
        // R17 deliberately changes two old Timer-ban expectations; all other retained-action assertions remain.
        actionTest=replace(actionTest,"  require(queue.size()==1,\"Genuine native Timer action was disabled\");queue.removeFirst().run();","  require(queue.isEmpty(),\"R17 timing-only evidence queued a ban\");");
        actionTest=replace(actionTest,"  require(kicks.size()==1&&realTimerOwner.history().size()==2,\"Genuine Timer action no longer bans\");","  require(kicks.isEmpty()&&realTimerOwner.history().size()==0,\"R17 timing-only evidence fabricated a sanction/history entry\");");
        Path generatedTests=out.resolve("generated-tests/dev/aegisac");Files.createDirectories(generatedTests);Path newActionTest=generatedTests.resolve("NativeActionRegressionTest.java");Files.writeString(newActionTest,actionTest);
        testFiles.remove(oldActionTest);testFiles.add(newActionTest);testFiles.addAll(files(root.resolve("src/test/java"),".java"));
        String testCp=classpath(List.of(jar,inputs.resolve("paper-api.jar"),inputs.resolve("paper-api-dependencies.jar")));
        compile(testFiles,tests,testCp,21);
        String java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        StringBuilder results=new StringBuilder("{\n  \"r16_engine_classes_unchanged\": "+unchanged+",\n  \"java\": \""+System.getProperty("java.version")+"\",\n  \"suites\": [\n");
        List<String> suites=List.of("NativeActionRegressionTest","DispatcherHistoryRegressionTest","TimerSafetyRegressionTest","VpnSourceRecoveryRegressionTest","RemoteInteractionRegressionTest","AntiXrayRegressionTest","R17TimingSafetyRegressionTest");
        boolean first=true;
        for(String suite:suites) {
            List<String> command=new ArrayList<>(List.of(java,"-Xmx256m","-XX:ActiveProcessorCount=2","-Xverify:all","-Dfile.encoding=UTF-8","-Djava.io.tmpdir="+temp,"-cp",tests+File.pathSeparator+testCp,"dev.aegisac."+suite));
            if(suite.equals("TimerSafetyRegressionTest")){command.add(repo.resolve("r16/original-punishments.yml").toString());command.add(repo.resolve("r16/port-source/main/resources/native-punishments.yml").toString());}
            Path log=out.resolve(suite+".log");Process p=new ProcessBuilder(command).directory(repo.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();int code=p.waitFor();String report=Files.readString(log);
            System.out.println(suite+": exit="+code+" "+report.lines().filter(l->l.contains("PASS")).reduce("",(a,b)->a+" "+b));
            if(!first)results.append(",\n");first=false;results.append("    {\"suite\":\"").append(suite).append("\",\"exit\":").append(code).append("}");
            if(code!=0){System.out.println(report);throw new AssertionError(suite+" failed");}
        }
        results.append("\n  ]\n}\n");Files.writeString(out.resolve("regression-results.json"),results);
        String sha=hash(jar);Files.writeString(out.resolve("SHA256SUMS.txt"),sha+"  "+jar.getFileName()+"\n");
        System.out.println("BUILT "+jar+" bytes="+Files.size(jar)+" sha256="+sha+" unchanged R16 engine classes="+unchanged);
    }
    private static void compile(List<Path> sources,Path dest,String cp,int release)throws IOException {
        List<String> args=new ArrayList<>(List.of("--release",Integer.toString(release),"-proc:none","-encoding","UTF-8","-cp",cp,"-d",dest.toString()));sources.forEach(p->args.add(p.toString()));
        if(ToolProvider.getSystemJavaCompiler()==null)throw new IllegalStateException("Use a full JDK");
        int code=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,args.toArray(String[]::new));if(code!=0)throw new IOException("Compilation failed");
    }
    private static String classpath(List<Path> paths){return String.join(File.pathSeparator,paths.stream().map(Path::toString).toList());}
    private static List<Path> files(Path root,String suffix)throws IOException {try(var stream=Files.walk(root)){return new ArrayList<>(stream.filter(Files::isRegularFile).filter(p->suffix==null||p.toString().endsWith(suffix)).sorted().toList());}}
    private static String read(Path p)throws IOException{return Files.readString(p).replace("\r\n","\n");}
    private static String replace(String source,String from,String to){if(!source.contains(from))throw new IllegalStateException("Source integration marker missing: "+from);return source.replace(from,to);}
    private static void download(String url,Path target)throws Exception {
        var client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(30)).build();
        var response=client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(3)).header("User-Agent","AegisAC-R17-build/1.0").GET().build(),HttpResponse.BodyHandlers.ofFile(target));
        if(response.statusCode()!=200)throw new IOException("Build input download HTTP "+response.statusCode());
    }
    private static String hash(Path path)throws Exception {MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(path)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}return HexFormat.of().formatHex(md.digest());}
    private static void verify(Path path,String expected)throws Exception {if(!hash(path).equals(expected))throw new SecurityException("Pinned input mismatch: "+path.getFileName());}
}
