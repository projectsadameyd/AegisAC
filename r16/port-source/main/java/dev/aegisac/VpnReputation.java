package dev.aegisac;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodySubscriber;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

final class VpnReputation implements AutoCloseable {
   private final VpnReputation.Policy policy;
   private final VpnReputation.Lookup lookup;
   private final LongSupplier clock;
   private final ThreadPoolExecutor workers;
   private final Map<String, VpnReputation.Entry> cache = new LinkedHashMap<>(16, 0.75F, true);
   private final ConcurrentMap<String, CompletableFuture<VpnReputation.Decision>> pending = new ConcurrentHashMap<>();
   private final AtomicLong queries = new AtomicLong();
   private final AtomicLong hits = new AtomicLong();
   private final AtomicLong failures = new AtomicLong();
   private final AtomicLong generation = new AtomicLong();
   private final AtomicLong cooldownUntil = new AtomicLong(Long.MIN_VALUE);
   private volatile boolean closed;

   VpnReputation(VpnReputation.Policy var1, VpnReputation.Lookup var2) {
      this(var1, var2, () -> System.nanoTime() / 1000000L);
   }

   VpnReputation(VpnReputation.Policy var1, VpnReputation.Lookup var2, LongSupplier var3) {
      this.policy = var1;
      this.lookup = var2;
      this.clock = var3;
      this.workers = new ThreadPoolExecutor(4, 4, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), var0 -> {
         Thread var1x = new Thread(var0, "AegisAC-Connection");
         var1x.setDaemon(true);
         return var1x;
      }, new AbortPolicy());
      this.workers.allowCoreThreadTimeOut(true);
   }

   CompletableFuture<VpnReputation.Decision> screen(InetAddress var1) {
      if (this.closed) {
         return CompletableFuture.completedFuture(VpnReputation.Decision.unknown("Screening stopped"));
      }

      if (var1 == null) {
         return CompletableFuture.completedFuture(VpnReputation.Decision.unknown("Address unavailable"));
      }

      String var2 = var1.getHostAddress();
      if (!localAddress(var1) && !this.policy.allowedIps().contains(var2)) {
         long var3 = this.clock.getAsLong();
         synchronized (this.cache) {
            VpnReputation.Entry var6 = this.cache.get(var2);
            if (var6 != null && var6.expires() > var3) {
               this.hits.incrementAndGet();
               return CompletableFuture.completedFuture(var6.decision());
            }

            if (var6 != null) {
               this.cache.remove(var2);
            }
         }

         if (var3 < this.cooldownUntil.get()) {
            return CompletableFuture.completedFuture(VpnReputation.Decision.unknown("Provider temporarily unavailable"));
         }

         CompletableFuture var13 = new CompletableFuture();
         CompletableFuture var14 = this.pending.putIfAbsent(var2, var13);
         if (var14 != null) {
            return var14;
         }

         var13.whenComplete((var3x, var4) -> this.pending.remove(var2, var13));
         long var7 = this.generation.get();

         try {
            this.workers.execute(() -> {
               VpnReputation.Decision var5;
               try {
                  if (this.closed) {
                     var13.complete(VpnReputation.Decision.unknown("Screening stopped"));
                     return;
                  }

                  this.queries.incrementAndGet();
                  var5 = parse(this.lookup.fetch(var2, this.policy), var2, this.policy);
               } catch (InterruptedException var8) {
                  Thread.currentThread().interrupt();
                  var5 = VpnReputation.Decision.unknown("Lookup interrupted");
               } catch (Exception var9) {
                  this.failures.incrementAndGet();
                  if (var9 instanceof VpnReputation.ProviderFailure var7x && var7x.retryMillis > 0L) {
                     this.cooldownUntil.accumulateAndGet(this.clock.getAsLong() + var7x.retryMillis, Math::max);
                  }

                  var5 = VpnReputation.Decision.unknown(var9 instanceof VpnReputation.ProviderFailure ? var9.getMessage() : "Lookup unavailable");
               }

               if (!this.closed && var7 == this.generation.get()) {
                  this.remember(var2, var5);
               }

               var13.complete(var5);
            });
         } catch (RejectedExecutionException var11) {
            this.failures.incrementAndGet();
            VpnReputation.Decision var10 = VpnReputation.Decision.unknown("Screening capacity reached");
            if (!this.closed) {
               this.remember(var2, var10);
            }

            var13.complete(var10);
         }

         return var13;
      } else {
         return CompletableFuture.completedFuture(new VpnReputation.Decision(VpnReputation.Outcome.CLEAR, "Allowlisted/local address"));
      }
   }

   private void remember(String var1, VpnReputation.Decision var2) {
      long var3 = var2.outcome() == VpnReputation.Outcome.UNKNOWN ? this.policy.errorTtlMillis() : this.policy.cacheTtlMillis();
      synchronized (this.cache) {
         this.cache.put(var1, new VpnReputation.Entry(var2, this.clock.getAsLong() + var3));

         while (this.cache.size() > this.policy.maxCacheEntries()) {
            this.cache.remove(this.cache.keySet().iterator().next());
         }
      }
   }

   VpnReputation.Health health() {
      synchronized (this.cache) {
         return new VpnReputation.Health(this.queries.get(), this.hits.get(), this.failures.get(), this.cache.size(), this.pending.size());
      }
   }

   void clearCache() {
      this.generation.incrementAndGet();
      synchronized (this.cache) {
         this.cache.clear();
      }

      this.pending.clear();
      this.cooldownUntil.set(Long.MIN_VALUE);
   }

   @Override
   public void close() {
      this.closed = true;
      this.generation.incrementAndGet();
      this.workers.shutdownNow();
      this.pending.values().forEach(var0 -> var0.complete(VpnReputation.Decision.unknown("Screening stopped")));
      this.pending.clear();
      synchronized (this.cache) {
         this.cache.clear();
      }
   }

   static InetAddress literal(String var0) {
      if (var0 != null && !var0.isBlank() && var0.equals(var0.trim())) {
         if (var0.indexOf(58) >= 0) {
            if (!var0.matches("[0-9a-fA-F:.]+")) {
               throw new IllegalArgumentException("Supply a literal IPv4 or IPv6 address.");
            }
         } else {
            String[] var1 = var0.split("\\.", -1);
            if (var1.length != 4) {
               throw new IllegalArgumentException("Supply a literal IPv4 or IPv6 address.");
            }

            for (String var5 : var1) {
               if (!var5.matches("[0-9]{1,3}") || Integer.parseInt(var5) > 255) {
                  throw new IllegalArgumentException("Supply a literal IPv4 or IPv6 address.");
               }
            }
         }

         try {
            return InetAddress.getByName(var0);
         } catch (UnknownHostException var6) {
            throw new IllegalArgumentException("Invalid IP address.");
         }
      } else {
         throw new IllegalArgumentException("Supply a literal IPv4 or IPv6 address.");
      }
   }

   static boolean localAddress(InetAddress var0) {
      if (!var0.isAnyLocalAddress() && !var0.isLoopbackAddress() && !var0.isLinkLocalAddress() && !var0.isSiteLocalAddress() && !var0.isMulticastAddress()) {
         byte[] var1 = var0.getAddress();
         return var1.length == 16 ? (var1[0] & 254) == 252 : (var1[0] & 255) == 0 || (var1[0] & 255) == 100 && (var1[1] & 192) == 64;
      } else {
         return true;
      }
   }

   static VpnReputation.Decision parse(String var0, String var1, VpnReputation.Policy var2) throws VpnReputation.ProviderFailure {
      if (var0 != null && var0.length() <= 65536) {
         try {
            JsonElement var3 = new JsonParser().parse(var0);
            if (!var3.isJsonObject()) {
               throw new IllegalArgumentException();
            }

            JsonObject var4 = var3.getAsJsonObject();
            String var5 = var4.get("status").getAsString();
            if (!var5.equals("ok") && !var5.equals("warning")) {
               throw new VpnReputation.ProviderFailure("Provider rejected the lookup", 60000L);
            }

            String var6 = var4.get("address").getAsString();
            if (!Arrays.equals(literal(var1).getAddress(), literal(var6).getAddress())) {
               throw new IllegalArgumentException();
            }

            JsonObject var7 = var4.getAsJsonObject("detections");
            boolean var8 = booleanFlag(var7, "vpn");
            boolean var9 = booleanFlag(var7, "proxy");
            boolean var10 = booleanFlag(var7, "tor");
            ArrayList var11 = new ArrayList();
            if (var2.blockVpn() && var8) {
               var11.add("VPN");
            }

            if (var2.blockProxy() && var9) {
               var11.add("Proxy");
            }

            if (var2.blockTor() && var10) {
               var11.add("Tor");
            }

            return var11.isEmpty()
               ? new VpnReputation.Decision(VpnReputation.Outcome.CLEAR, "No VPN/proxy detected")
               : new VpnReputation.Decision(VpnReputation.Outcome.BLOCKED, String.join(" / ", var11));
         } catch (VpnReputation.ProviderFailure var12) {
            throw var12;
         } catch (RuntimeException var13) {
            throw new VpnReputation.ProviderFailure("Invalid provider response", 0L);
         }
      } else {
         throw new VpnReputation.ProviderFailure("Invalid provider response", 0L);
      }
   }

   private static boolean booleanFlag(JsonObject var0, String var1) {
      JsonElement var2 = var0.get(var1);
      if (var2 != null && var2.isJsonPrimitive() && var2.getAsJsonPrimitive().isBoolean()) {
         return var2.getAsBoolean();
      } else {
         throw new IllegalArgumentException();
      }
   }

   record Decision(VpnReputation.Outcome outcome, String reason) {
      static VpnReputation.Decision unknown(String var0) {
         return new VpnReputation.Decision(VpnReputation.Outcome.UNKNOWN, var0);
      }
   }

   private record Entry(VpnReputation.Decision decision, long expires) {
   }

   record Health(long queries, long cacheHits, long failures, int cachedIps, int pendingIps) {
   }

   static final class HttpLookup implements VpnReputation.Lookup {
      private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1L)).followRedirects(Redirect.NEVER).build();
      private final URI fixtureEndpoint;

      HttpLookup() {
         this(null);
      }

      HttpLookup(URI var1) {
         this.fixtureEndpoint = var1;
      }

      static URI requestUri(String var0, VpnReputation.Policy var1) {
         VpnReputation.literal(var0);
         String var2 = "https://proxycheck.io/v3/" + URLEncoder.encode(var0, StandardCharsets.UTF_8) + "?short=1&p=0&tag=0&ver=24-June-2026";
         if (!var1.apiKey().isBlank()) {
            var2 = var2 + "&key=" + URLEncoder.encode(var1.apiKey(), StandardCharsets.UTF_8);
         }

         return URI.create(var2);
      }

      @Override
      public String fetch(String var1, VpnReputation.Policy var2) throws Exception {
         HttpRequest var3 = HttpRequest.newBuilder(this.fixtureEndpoint == null ? requestUri(var1, var2) : this.fixtureEndpoint)
            .timeout(Duration.ofMillis(var2.timeoutMillis()))
            .header("Accept", "application/json")
            .header("User-Agent", "AegisAC/4.2")
            .GET()
            .build();
         CompletableFuture var4 = HTTP.sendAsync(var3, var0 -> new VpnReputation.LimitedBody());

         HttpResponse var5;
         try {
            var5 = (HttpResponse)var4.get(var2.timeoutMillis(), TimeUnit.MILLISECONDS);
         } catch (TimeoutException var7) {
            var4.cancel(true);
            throw new HttpTimeoutException("Lookup deadline exceeded");
         } catch (InterruptedException var8) {
            var4.cancel(true);
            throw var8;
         }

         if (var5.statusCode() == 200) {
            return (String)var5.body();
         } else {
            throw new VpnReputation.ProviderFailure("Provider HTTP " + var5.statusCode(), var5.statusCode() != 429 && var5.statusCode() != 403 ? 5000L : 60000L);
         }
      }
   }

   static final class LimitedBody implements BodySubscriber<String> {
      private final CompletableFuture<String> body = new CompletableFuture<>();
      private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      private Subscription subscription;

      @Override
      public CompletionStage<String> getBody() {
         return this.body;
      }

      @Override
      public void onSubscribe(Subscription var1) {
         this.subscription = var1;
         var1.request(1L);
      }

      public void onNext(List<ByteBuffer> var1) {
         if (!this.body.isDone()) {
            for (ByteBuffer var3 : var1) {
               if (var3.remaining() > 65536 - this.bytes.size()) {
                  this.subscription.cancel();
                  this.body.completeExceptionally(new IOException("Provider response too large"));
                  return;
               }

               byte[] var4 = new byte[var3.remaining()];
               var3.get(var4);
               this.bytes.write(var4, 0, var4.length);
            }

            this.subscription.request(1L);
         }
      }

      @Override
      public void onError(Throwable var1) {
         this.body.completeExceptionally(var1);
      }

      @Override
      public void onComplete() {
         this.body.complete(this.bytes.toString(StandardCharsets.UTF_8));
      }
   }

   interface Lookup {
      String fetch(String var1, VpnReputation.Policy var2) throws Exception;
   }

   enum Outcome {
      CLEAR,
      BLOCKED,
      UNKNOWN;
   }

   record Policy(
      boolean blockVpn,
      boolean blockProxy,
      boolean blockTor,
      int timeoutMillis,
      long cacheTtlMillis,
      long errorTtlMillis,
      int maxCacheEntries,
      Set<String> allowedIps,
      String apiKey
   ) {
      Policy(
         boolean blockVpn,
         boolean blockProxy,
         boolean blockTor,
         int timeoutMillis,
         long cacheTtlMillis,
         long errorTtlMillis,
         int maxCacheEntries,
         Set<String> allowedIps,
         String apiKey
      ) {
         if (timeoutMillis >= 100 && timeoutMillis <= 10000 && cacheTtlMillis >= 1L && errorTtlMillis >= 1L && maxCacheEntries >= 1 && maxCacheEntries <= 8192) {
            HashSet var12 = new HashSet();

            for (String var14 : allowedIps) {
               var12.add(VpnReputation.literal(var14).getHostAddress());
            }

            allowedIps = Set.copyOf(var12);
            apiKey = apiKey == null ? "" : apiKey.trim();
            this.blockVpn = blockVpn;
            this.blockProxy = blockProxy;
            this.blockTor = blockTor;
            this.timeoutMillis = timeoutMillis;
            this.cacheTtlMillis = cacheTtlMillis;
            this.errorTtlMillis = errorTtlMillis;
            this.maxCacheEntries = maxCacheEntries;
            this.allowedIps = allowedIps;
            this.apiKey = apiKey;
         } else {
            throw new IllegalArgumentException("Invalid connection screening limits.");
         }
      }
   }

   static final class ProviderFailure extends IOException {
      final long retryMillis;

      ProviderFailure(String var1, long var2) {
         super(var1);
         this.retryMillis = var2;
      }
   }
}
