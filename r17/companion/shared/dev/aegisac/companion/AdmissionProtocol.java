package dev.aegisac.companion;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** A bounded, nonce-bound report transport. This is not remote attestation. */
public final class AdmissionProtocol {
    public static final String BUILD="r17-preview2", CHALLENGE="aegisac:challenge_v1", REPORT="aegisac:report_v1";
    public static final int VERSION=1, POLICY=1, NONCE_BYTES=32, MAX_BYTES=4096;
    public enum Outcome { NO_KNOWN_MATCH, KNOWN_MATCH, INCOMPLETE }
    public record Challenge(byte[] nonce) {
        public Challenge { if(nonce.length!=NONCE_BYTES)throw new IllegalArgumentException("nonce");nonce=nonce.clone(); }
        @Override public byte[] nonce(){return nonce.clone();}
    }
    public record Report(byte[] nonce,String build,int policy,Outcome outcome,int roots,int classes,long millis,List<String> findings) {
        public Report {
            if(nonce.length!=NONCE_BYTES||build==null||build.length()>64||policy<0||outcome==null||roots<0||roots>512||classes<0||classes>200000||millis<0||millis>60000||findings==null||findings.size()>8)throw new IllegalArgumentException("report bounds");
            nonce=nonce.clone();findings=List.copyOf(findings);
            for(String finding:findings)if(!finding.matches("[A-Z0-9_]{1,48}"))throw new IllegalArgumentException("finding");
            if((outcome==Outcome.KNOWN_MATCH)!=!findings.isEmpty())throw new IllegalArgumentException("contradictory report");
        }
        @Override public byte[] nonce(){return nonce.clone();}
    }
    public static byte[] encode(Challenge c)throws IOException {return write(out->{out.writeInt(0x41474331);out.writeInt(VERSION);out.write(c.nonce());out.writeInt(POLICY);});}
    public static Challenge challenge(byte[] bytes)throws IOException {
        try(var in=input(bytes)){if(in.readInt()!=0x41474331||in.readInt()!=VERSION)throw new IOException("challenge version");byte[] nonce=readNonce(in);if(in.readInt()!=POLICY||in.available()!=0)throw new IOException("challenge policy");return new Challenge(nonce);}
    }
    public static byte[] encode(Report r)throws IOException {
        return write(out->{out.writeInt(0x41475231);out.writeInt(VERSION);out.write(r.nonce());out.writeUTF(r.build());out.writeInt(r.policy());out.writeByte(r.outcome().ordinal());out.writeInt(r.roots());out.writeInt(r.classes());out.writeLong(r.millis());out.writeByte(r.findings().size());for(String f:r.findings())out.writeUTF(f);});
    }
    public static Report report(byte[] bytes)throws IOException {
        try(var in=input(bytes)) {
            if(in.readInt()!=0x41475231||in.readInt()!=VERSION)throw new IOException("report version");
            byte[] nonce=readNonce(in);String build=in.readUTF();int policy=in.readInt(),status=in.readUnsignedByte(),roots=in.readInt(),classes=in.readInt();long millis=in.readLong();int n=in.readUnsignedByte();
            if(status>=Outcome.values().length||n>8)throw new IOException("report bounds");List<String> findings=new ArrayList<>();for(int i=0;i<n;i++)findings.add(in.readUTF());
            if(in.available()!=0)throw new IOException("report trailing bytes");
            try{return new Report(nonce,build,policy,Outcome.values()[status],roots,classes,millis,findings);}catch(IllegalArgumentException e){throw new IOException("invalid report",e);}
        }
    }
    public static boolean matches(Challenge c,Report r){return MessageDigest.isEqual(c.nonce(),r.nonce());}
    public static boolean supported(Report r){return r.build().equals(BUILD)&&r.policy()==POLICY;}
    private interface Writer {void write(DataOutputStream out)throws IOException;}
    private static byte[] write(Writer writer)throws IOException {var b=new ByteArrayOutputStream();try(var out=new DataOutputStream(b)){writer.write(out);}if(b.size()>MAX_BYTES)throw new IOException("payload too large");return b.toByteArray();}
    private static DataInputStream input(byte[] bytes)throws IOException {if(bytes==null||bytes.length>MAX_BYTES)throw new IOException("payload too large");return new DataInputStream(new ByteArrayInputStream(bytes));}
    private static byte[] readNonce(DataInputStream in)throws IOException {byte[] n=in.readNBytes(NONCE_BYTES);if(n.length!=NONCE_BYTES)throw new EOFException();return n;}
    private AdmissionProtocol(){}
}
