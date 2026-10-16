import dev.aegisac.companion.*;
import java.lang.reflect.*;
import java.util.*;

/** Runs the actual production payload codecs against Minecraft and Fabric interfaces, without a game window. */
public final class CompanionCodecSmoke {
    static int assertions;
    static void require(boolean okay,String reason){assertions++;if(!okay)throw new AssertionError(reason);}
    public static void main(String[] args)throws Exception {
        byte[] nonce=new byte[32];new Random(17).nextBytes(nonce);
        byte[] challenge=AdmissionProtocol.encode(new AdmissionProtocol.Challenge(nonce));
        roundTrip("ChallengePayload",challenge);
        var report=new AdmissionProtocol.Report(nonce,AdmissionProtocol.BUILD,1,AdmissionProtocol.Outcome.KNOWN_MATCH,2,1200,20,List.of("METEOR_CODE_V1"));
        roundTrip("ReportPayload",AdmissionProtocol.encode(report));
        try{decode("ChallengePayload",new byte[AdmissionProtocol.MAX_BYTES+1]);throw new AssertionError("Oversized codec payload accepted");}catch(InvocationTargetException e){require(e.getCause() instanceof IllegalArgumentException,"Codec size error");}
        Object registration=Class.forName("dev.aegisac.companion.AegisCompanionClient").getMethod("channelRegistration").invoke(null);
        Object registerCodec=registration.getClass().getField("REGISTER_CODEC").get(null),registerBuffer=buffer(new byte[0]);
        Method encode=Arrays.stream(Class.forName("net.minecraft.class_9139").getMethods()).filter(m->m.getParameterCount()==2&&m.getReturnType()==void.class).findFirst().orElseThrow();
        encode.invoke(registerCodec,registerBuffer,registration);Class<?> byteBuf=Class.forName("io.netty.buffer.ByteBuf");int n=(Integer)byteBuf.getMethod("readableBytes").invoke(registerBuffer);byte[] wire=new byte[n];byteBuf.getMethod("readBytes",byte[].class).invoke(registerBuffer,(Object)wire);byteBuf.getMethod("release").invoke(registerBuffer);
        require(Arrays.equals(wire,AdmissionProtocol.CHALLENGE.getBytes(java.nio.charset.StandardCharsets.UTF_8)),"Fabric receiver registration is not Paper-compatible raw UTF-8");
        System.out.println("PASS "+assertions+" actual production Minecraft/Fabric payload-codec assertions");
    }
    static void roundTrip(String name,byte[] bytes)throws Exception {
        Class<?> payload=Class.forName("dev.aegisac.companion.AegisCompanionClient$"+name);
        Object decoded=decode(name,bytes);require(Arrays.equals(bytes,(byte[])payload.getMethod("bytes").invoke(decoded)),"Decode added or removed wire bytes");
        Object codec=payload.getField("CODEC").get(null),buffer=buffer(new byte[0]);
        Method encode=Arrays.stream(Class.forName("net.minecraft.class_9139").getMethods()).filter(m->m.getParameterCount()==2&&m.getReturnType()==void.class).findFirst().orElseThrow();
        encode.invoke(codec,buffer,decoded);Class<?> byteBuf=Class.forName("io.netty.buffer.ByteBuf");int n=(Integer)byteBuf.getMethod("readableBytes").invoke(buffer);byte[] encoded=new byte[n];byteBuf.getMethod("readBytes",byte[].class).invoke(buffer,(Object)encoded);
        require(Arrays.equals(bytes,encoded),"Encode differs from server's admission protocol");byteBuf.getMethod("release").invoke(buffer);
        byte[] exposed=(byte[])payload.getMethod("bytes").invoke(decoded);exposed[0]^=1;require(Arrays.equals(bytes,(byte[])payload.getMethod("bytes").invoke(decoded)),"Payload bytes are mutable");
    }
    static Object decode(String name,byte[] bytes)throws Exception {
        Class<?> payload=Class.forName("dev.aegisac.companion.AegisCompanionClient$"+name);Object codec=payload.getField("CODEC").get(null),buffer=buffer(bytes);
        Method decode=Arrays.stream(Class.forName("net.minecraft.class_9139").getMethods()).filter(m->m.getParameterCount()==1&&!Modifier.isStatic(m.getModifiers())&&m.getReturnType()==Object.class).findFirst().orElseThrow();
        try{return decode.invoke(codec,buffer);}finally{Class.forName("io.netty.buffer.ByteBuf").getMethod("release").invoke(buffer);}
    }
    static Object buffer(byte[] bytes)throws Exception {Class<?> byteBuf=Class.forName("io.netty.buffer.ByteBuf");Object buffer=Class.forName("io.netty.buffer.Unpooled").getMethod("wrappedBuffer",byte[].class).invoke(null,(Object)bytes);if(bytes.length==0)buffer=Class.forName("io.netty.buffer.Unpooled").getMethod("buffer").invoke(null);return Class.forName("net.minecraft.class_2540").getConstructor(byteBuf).newInstance(buffer);}
}
