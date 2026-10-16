package dev.aegisac;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Explicit, persistent exemptions from companion admission only. */
final class CompanionExemptions {
    private static final String KEY="exempt-usernames";
    private static final int MAX_NAMES=4096;
    private CompanionExemptions(){}

    static String normalize(String name) {
        if(name==null||!name.matches("[A-Za-z0-9_]{3,16}"))
            throw new IllegalArgumentException("Use a Minecraft username with 3..16 letters, numbers or underscores.");
        return name.toLowerCase(Locale.ROOT);
    }
    static boolean contains(Set<String> names,String name) {
        return name!=null&&names.contains(name.toLowerCase(Locale.ROOT));
    }
    static Set<String> read(YamlConfiguration yaml)throws IOException {
        Object raw=yaml.get(KEY);if(raw==null)return Set.of();
        if(!(raw instanceof List<?> values))throw new IOException(KEY+" must be a YAML list of usernames.");
        if(values.size()>MAX_NAMES)throw new IOException(KEY+" may contain at most "+MAX_NAMES+" usernames.");
        Set<String> names=new TreeSet<>();
        for(Object value:values) {
            if(!(value instanceof String name))throw new IOException(KEY+" entries must be quoted usernames.");
            try{names.add(normalize(name));}catch(IllegalArgumentException e){throw new IOException("Invalid "+KEY+" entry: "+name,e);}
        }
        return Set.copyOf(names);
    }
    static boolean update(File file,String username,boolean exempt)throws IOException {
        String name=normalize(username);YamlConfiguration yaml=VpnGate.readStrict(file);
        Set<String> names=new TreeSet<>(read(yaml));
        boolean changed=exempt?names.add(name):names.remove(name);if(!changed)return false;
        if(names.size()>MAX_NAMES)throw new IOException("The exemption list is full.");
        yaml.set(KEY,new ArrayList<>(names));
        Path target=file.toPath().toAbsolutePath();
        Path temporary=Files.createTempFile(target.getParent(),"client-guard-",".tmp");
        try {
            Files.writeString(temporary,yaml.saveToString(),StandardCharsets.UTF_8);
            try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
        return true;
    }
}
