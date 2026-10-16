package dev.aegisac;

import dev.aegisac.companion.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.tools.ToolProvider;

public final class CompanionAdmissionRegressionTest {
    private static int assertions;
    private static void require(boolean okay,String message){assertions++;if(!okay)throw new AssertionError(message);}
    private static void invalid(byte[] bytes){try{AdmissionProtocol.report(bytes);throw new AssertionError("Malformed report accepted");}catch(IOException expected){assertions++;}}
    public static void main(String[] args)throws Exception {
        byte[] nonce=new byte[32];new Random(17).nextBytes(nonce);var challenge=new AdmissionProtocol.Challenge(nonce);
        require(Arrays.equals(nonce,AdmissionProtocol.challenge(AdmissionProtocol.encode(challenge)).nonce()),"challenge round trip");
        var clear=new AdmissionProtocol.Report(nonce,AdmissionProtocol.BUILD,1,AdmissionProtocol.Outcome.NO_KNOWN_MATCH,6,2000,31,List.of());
        var decoded=AdmissionProtocol.report(AdmissionProtocol.encode(clear));require(AdmissionProtocol.matches(challenge,decoded)&&AdmissionProtocol.supported(decoded),"report round trip");
        byte[] other=nonce.clone();other[3]^=1;require(!AdmissionProtocol.matches(new AdmissionProtocol.Challenge(other),decoded),"old connection report replay");
        require(!AdmissionProtocol.supported(new AdmissionProtocol.Report(nonce,"old-build",1,AdmissionProtocol.Outcome.NO_KNOWN_MATCH,6,2000,31,List.of())),"old companion accepted");
        nonce[2]^=1;require(AdmissionProtocol.matches(challenge,decoded),"mutable nonce alias");
        byte[] encoded=AdmissionProtocol.encode(clear);for(int i=0;i<encoded.length;i++)invalid(Arrays.copyOf(encoded,i));
        byte[] trailing=Arrays.copyOf(encoded,encoded.length+1);invalid(trailing);invalid(new byte[AdmissionProtocol.MAX_BYTES+1]);
        Random random=new Random(9);for(int i=0;i<100;i++){byte[] fuzz=new byte[random.nextInt(512)];random.nextBytes(fuzz);invalid(fuzz);}
        try{new AdmissionProtocol.Report(nonce,"x",1,AdmissionProtocol.Outcome.NO_KNOWN_MATCH,1,1,1,List.of("METEOR_CODE_V1"));throw new AssertionError("contradiction");}catch(IllegalArgumentException e){assertions++;}
        Path tmp=Files.createTempDirectory("aegis-companion-test-");
        // These are compiled synthetic shapes for rule plumbing, not real-client detection claims.
        for(var rule:CheatSignatures.RULES) {
            Path sources=tmp.resolve(rule.id()+"/sources"),classes=tmp.resolve(rule.id()+"/classes");Files.createDirectories(classes);
            List<String> names=new ArrayList<>(rule.required());names.addAll(rule.additional());List<String> javac=new ArrayList<>(List.of("--release","17","-d",classes.toString()));
            for(String suffix:names){String name="relocated/"+suffix;Path file=sources.resolve(name+".java");Files.createDirectories(file.getParent());int slash=name.lastIndexOf('/');Files.writeString(file,"package "+name.substring(0,slash).replace('/','.')+"; public class "+name.substring(slash+1)+" {}\n");javac.add(file.toString());}
            require(ToolProvider.getSystemJavaCompiler().run(null,null,null,javac.toArray(String[]::new))==0,"fixture compile");
            var detected=scan(classes);require(detected.findings().equals(List.of(rule.id())),"relocated code family not found "+rule.id());
            Path jar=tmp.resolve(rule.id()+"-renamed-as-sodium.jar");try(var out=new ZipOutputStream(Files.newOutputStream(jar));var paths=Files.walk(classes)){for(Path p:paths.filter(Files::isRegularFile).toList()){out.putNextEntry(new ZipEntry(classes.relativize(p).toString().replace('\\','/')));out.write(Files.readAllBytes(p));out.closeEntry();}}
            require(scan(jar).findings().equals(List.of(rule.id())),"renamed jar escaped");
            Files.delete(classes.resolve("relocated/"+rule.required().get(0)+".class"));require(scan(classes).outcome()==AdmissionProtocol.Outcome.NO_KNOWN_MATCH,"isolated capability names convicted");
        }
        Path ordinary=tmp.resolve("ordinary");Files.createDirectories(ordinary);Files.writeString(ordinary.resolve("meteor-client.txt"),"KillAura Flight Xray scripts capabilities");
        require(scan(ordinary).outcome()==AdmissionProtocol.Outcome.NO_KNOWN_MATCH,"text or filenames caused block");
        require(scan(tmp.resolve("missing.jar")).outcome()==AdmissionProtocol.Outcome.INCOMPLETE,"unreadable source passed");
        require(new LoadedCodeScanner(System.nanoTime()-1,()->false).scan(List.of(ordinary)).outcome()==AdmissionProtocol.Outcome.INCOMPLETE,"expired budget passed");
        require(new LoadedCodeScanner(System.nanoTime()+1_000_000_000L,()->true).scan(List.of(ordinary)).outcome()==AdmissionProtocol.Outcome.INCOMPLETE,"cancelled scan passed");
        require(scan(Path.of(args[1])).outcome()==AdmissionProtocol.Outcome.NO_KNOWN_MATCH,"ordinary API false positive");
        if(!Files.isRegularFile(Path.of(args[0])))throw new AssertionError("Official Meteor test artifact is required for local validation");
        var meteor=scan(Path.of(args[0]));require(meteor.findings().contains("METEOR_CODE_V1"),"official Meteor jar was not detected: "+meteor);
        Path renamed=tmp.resolve("performance-mod.jar");Files.copy(Path.of(args[0]),renamed);require(scan(renamed).findings().contains("METEOR_CODE_V1"),"official Meteor renamed jar escaped");
        require(scan(Path.of(args[2])).findings().contains("METEOR_CODE_V1"),"official Meteor 1.21.11-86 escaped");
        Path modernRenamed=tmp.resolve("shader-helper.jar");Files.copy(Path.of(args[2]),modernRenamed);require(scan(modernRenamed).findings().contains("METEOR_CODE_V1"),"renamed Meteor 1.21.11-86 escaped");
        require(scan(Path.of(args[3])).outcome()==AdmissionProtocol.Outcome.NO_KNOWN_MATCH,"official Sodium false positive");
        System.out.println("PASS "+assertions+" companion protocol/scanner assertions; official Meteor artifact detected including renamed copy; scan="+meteor.millis()+"ms");
    }
    private static LoadedCodeScanner.Result scan(Path p){return new LoadedCodeScanner(System.nanoTime()+8_000_000_000L,()->false).scan(List.of(p));}
}
