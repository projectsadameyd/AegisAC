package dev.aegisac.companion;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.zip.*;

/** Reads only paths supplied by the running mod loader. Never executes inspected code. */
public final class LoadedCodeScanner {
    public record Result(AdmissionProtocol.Outcome outcome,int roots,int classes,long millis,List<String> findings) {}
    private static final int MAX_ROOTS=512,MAX_ENTRIES=200000,MAX_CLASS_BYTES=1048576;
    private final long deadline;private final BooleanSupplier cancelled;
    private int roots,entries,classes;private boolean incomplete;private final Set<String> hits=new LinkedHashSet<>();
    public LoadedCodeScanner(long deadlineNanos,BooleanSupplier cancelled){this.deadline=deadlineNanos;this.cancelled=cancelled;}
    public Result scan(Collection<Path> suppliedRoots) {
        long start=System.nanoTime();
        try {
            Set<Path> unique=new LinkedHashSet<>();for(Path p:suppliedRoots){if(p==null||unique.size()>=MAX_ROOTS)throw new IOException("root limit");unique.add(p.toAbsolutePath().normalize());}
            for(Path root:unique){check();if(++roots>MAX_ROOTS)throw new IOException("root limit");Set<String> valid=new HashSet<>();
                if(Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))scanDirectory(root,valid);
                else if(Files.isRegularFile(root,LinkOption.NOFOLLOW_LINKS))scanArchive(root,valid);
                else throw new IOException("unreadable mod origin");
                hits.addAll(CheatSignatures.match(valid));
            }
        }catch(IOException|RuntimeException error){incomplete=true;}
        long elapsed=Math.min(60000,Math.max(0,(System.nanoTime()-start)/1000000));
        return new Result(!hits.isEmpty()?AdmissionProtocol.Outcome.KNOWN_MATCH:incomplete?AdmissionProtocol.Outcome.INCOMPLETE:AdmissionProtocol.Outcome.NO_KNOWN_MATCH,roots,classes,elapsed,List.copyOf(hits));
    }
    private void scanDirectory(Path root,Set<String> valid)throws IOException {
        try(var paths=Files.walk(root,32)) {
            var it=paths.iterator();while(it.hasNext()){check();Path file=it.next();count();if(Files.isSymbolicLink(file))throw new IOException("symbolic mod entry");
                if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||!file.toString().endsWith(".class"))continue;
                String name=root.relativize(file).toString().replace('\\','/');name=name.substring(0,name.length()-6);classes++;
                if(CheatSignatures.candidate(name)){if(Files.size(file)>MAX_CLASS_BYTES)throw new IOException("class size");try(var in=Files.newInputStream(file)){validate(in,name,valid);}}
            }
        }
    }
    private void scanArchive(Path root,Set<String> valid)throws IOException {
        long size=Files.size(root),modified=Files.getLastModifiedTime(root).toMillis();
        try(ZipFile zip=new ZipFile(root.toFile())) {
            var it=zip.entries();while(it.hasMoreElements()){check();ZipEntry e=it.nextElement();count();if(e.isDirectory()||!e.getName().endsWith(".class"))continue;
                String name=e.getName().substring(0,e.getName().length()-6);classes++;
                if(CheatSignatures.candidate(name)){if(e.getSize()>MAX_CLASS_BYTES)throw new IOException("class size");try(var in=zip.getInputStream(e)){validate(in,name,valid);}}
            }
        }
        if(size!=Files.size(root)||modified!=Files.getLastModifiedTime(root).toMillis())throw new IOException("mod changed while scanning");
    }
    private void validate(InputStream source,String entry,Set<String> valid)throws IOException {
        byte[] bytes=source.readNBytes(MAX_CLASS_BYTES+1);if(bytes.length>MAX_CLASS_BYTES)throw new IOException("class size");
        String actual=className(bytes);if(!entry.equals(actual))throw new IOException("class name mismatch");valid.add(actual);
    }
    static String className(byte[] bytes)throws IOException {
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes))) {
            if(in.readInt()!=0xCAFEBABE)throw new IOException("class header");in.readUnsignedShort();in.readUnsignedShort();int n=in.readUnsignedShort();if(n<2||n>32768)throw new IOException("constant pool");
            Object[] pool=new Object[n];
            for(int i=1;i<n;i++){int tag=in.readUnsignedByte();switch(tag){case 1->pool[i]=in.readUTF();case 7->pool[i]=in.readUnsignedShort();case 3,4,9,10,11,12,17,18->in.skipNBytes(4);case 5,6->{in.skipNBytes(8);i++;}case 8,16,19,20->in.skipNBytes(2);case 15->in.skipNBytes(3);default->throw new IOException("constant pool tag");}}
            in.readUnsignedShort();int self=in.readUnsignedShort();in.readUnsignedShort();
            if(self<=0||self>=n||!(pool[self] instanceof Integer utf)||utf<=0||utf>=n||!(pool[utf] instanceof String name)||name.isEmpty())throw new IOException("class identity");return name;
        }
    }
    private void count()throws IOException {if(++entries>MAX_ENTRIES||classes>MAX_ENTRIES)throw new IOException("entry limit");}
    private void check()throws IOException {if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted()||System.nanoTime()>deadline)throw new IOException("scan incomplete");}
}
