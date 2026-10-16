package dev.aegisac.companion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.impl.networking.RegistrationPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AegisCompanionClient implements ClientModInitializer {
    private static final Logger LOG=LoggerFactory.getLogger("AegisAC Companion");
    private final AtomicLong connectionEpoch=new AtomicLong();
    private final ThreadPoolExecutor scanner=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{Thread t=new Thread(r,"AegisAC-loaded-mod-check");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    public record ChallengePayload(byte[] bytes) implements CustomPacketPayload {
        public static final Type<ChallengePayload> TYPE=new Type<>(Identifier.parse(AdmissionProtocol.CHALLENGE));
        public static final StreamCodec<FriendlyByteBuf,ChallengePayload> CODEC=StreamCodec.of((buf,p)->buf.writeBytes(p.bytes()),buf->new ChallengePayload(read(buf)));
        public ChallengePayload {bytes=bytes.clone();}
        @Override public byte[] bytes(){return bytes.clone();}
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record ReportPayload(byte[] bytes) implements CustomPacketPayload {
        public static final Type<ReportPayload> TYPE=new Type<>(Identifier.parse(AdmissionProtocol.REPORT));
        public static final StreamCodec<FriendlyByteBuf,ReportPayload> CODEC=StreamCodec.of((buf,p)->buf.writeBytes(p.bytes()),buf->new ReportPayload(read(buf)));
        public ReportPayload {bytes=bytes.clone();}
        @Override public byte[] bytes(){return bytes.clone();}
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    private static byte[] read(FriendlyByteBuf buf){int n=buf.readableBytes();if(n>AdmissionProtocol.MAX_BYTES)throw new IllegalArgumentException("AegisAC payload exceeds limit");byte[] data=new byte[n];buf.readBytes(data);return data;}
    // Fabric normally announces configuration receivers after a server registration payload.
    // Paper gates outgoing messages on the client's advertised channels, so announce ours at INIT.
    // Use Fabric's existing reserved-channel payload/codec; do not register a competing codec.
    public static RegistrationPayload channelRegistration(){return new RegistrationPayload(RegistrationPayload.REGISTER,List.of(Identifier.parse(AdmissionProtocol.CHALLENGE)));}
    @Override public void onInitializeClient() {
        PayloadTypeRegistry.configurationS2C().register(ChallengePayload.TYPE,ChallengePayload.CODEC);
        PayloadTypeRegistry.configurationC2S().register(ReportPayload.TYPE,ReportPayload.CODEC);
        ClientConfigurationConnectionEvents.INIT.register((handler,client)->{
            connectionEpoch.incrementAndGet();
            ClientConfigurationNetworking.getSender().sendPacket(channelRegistration());
            LOG.info("AegisAC configuration receiver announced (preview2-hotfix1).");
        });
        ClientConfigurationConnectionEvents.DISCONNECT.register((handler,client)->{connectionEpoch.incrementAndGet();scanner.getQueue().clear();});
        ClientConfigurationNetworking.registerGlobalReceiver(ChallengePayload.TYPE,(payload,context)->{
            final AdmissionProtocol.Challenge challenge;try{challenge=AdmissionProtocol.challenge(payload.bytes());}catch(IOException e){LOG.warn("Unsupported AegisAC admission challenge; no report sent.");return;}
            LOG.info("AegisAC verification challenge received.");
            long epoch=connectionEpoch.get();
            List<Path> roots=new ArrayList<>();
            // Built-in Minecraft/JVM origins are not user mods. No recursive scan of the game directory.
            for(var mod:FabricLoader.getInstance().getAllMods())if(!mod.getMetadata().getType().equals("builtin"))roots.addAll(mod.getRootPaths());
            Runnable scan=()->{
                var result=new LoadedCodeScanner(System.nanoTime()+8_000_000_000L,()->connectionEpoch.get()!=epoch).scan(roots);
                if(connectionEpoch.get()!=epoch)return;
                var report=new AdmissionProtocol.Report(challenge.nonce(),AdmissionProtocol.BUILD,AdmissionProtocol.POLICY,result.outcome(),result.roots(),result.classes(),result.millis(),result.findings());
                try{context.responseSender().sendPacket(new ReportPayload(AdmissionProtocol.encode(report)));LOG.info("AegisAC loaded-mod check: {} ({} roots, {} ms)",result.outcome(),result.roots(),result.millis());}
                catch(IOException|RuntimeException error){LOG.warn("Could not send AegisAC admission result.",error);}
            };
            try{scanner.execute(scan);}catch(RejectedExecutionException busy){try{context.responseSender().sendPacket(new ReportPayload(AdmissionProtocol.encode(new AdmissionProtocol.Report(challenge.nonce(),AdmissionProtocol.BUILD,AdmissionProtocol.POLICY,AdmissionProtocol.Outcome.INCOMPLETE,0,0,0,List.of()))));}catch(IOException ignored){}}
        });
        LOG.info("AegisAC Companion ready. Known loaded-code signatures only; no remote-attestation guarantee.");
    }
}
