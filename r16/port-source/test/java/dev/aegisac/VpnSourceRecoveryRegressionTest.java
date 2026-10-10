package dev.aegisac;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class VpnSourceRecoveryRegressionTest {
    private static int assertions;
    private static void check(boolean condition, String name) {
        assertions++;
        if (!condition) throw new AssertionError(name);
    }
    private interface Checked { void run() throws Exception; }
    private static void rejects(Checked action, String name) throws Exception {
        boolean rejected=false;
        try { action.run(); } catch (Exception expected) { rejected=true; }
        check(rejected,name);
    }
    private static VpnReputation.Policy policy(boolean v, boolean p, boolean t, int timeout, Set<String> allow) {
        return new VpnReputation.Policy(v,p,t,timeout,1000,100,2,allow,"");
    }
    private static String response(String ip, int bits) {
        return "{\"status\":\"ok\",\"address\":\""+ip+"\",\"detections\":{\"vpn\":"+((bits&1)!=0)+",\"proxy\":"+((bits&2)!=0)+",\"tor\":"+((bits&4)!=0)+"}}";
    }
    public static void main(String[] args) throws Exception {
        var all=policy(true,true,true,2000,Set.of());
        for (int flags=0;flags<8;flags++) {
            for (int enabled=0;enabled<8;enabled++) {
                var decision=VpnReputation.parse(response("8.8.8.8",flags),"8.8.8.8",policy((enabled&1)!=0,(enabled&2)!=0,(enabled&4)!=0,2000,Set.of()));
                check(decision.outcome()==((flags&enabled)==0?VpnReputation.Outcome.CLEAR:VpnReputation.Outcome.BLOCKED),"flag policy "+flags+"/"+enabled);
            }
        }
        for (String bad: List.of("{",response("1.1.1.1",1),response("8.8.8.8",1).replace("\"ok\"","\"denied\""),response("8.8.8.8",1).replace("true","\"true\"")," ".repeat(65537))) {
            rejects(()->VpnReputation.parse(bad,"8.8.8.8",all),"invalid provider response");
        }
        check(VpnReputation.parse(response("2001:4860:4860::8888",0),"2001:4860:4860:0:0:0:0:8888",all).outcome()==VpnReputation.Outcome.CLEAR,"IPv6 identity");
        for (String bad:List.of("example.com","8.8.8.8?key=abc"," 8.8.8.8","8.8.8.8 ","1.2.3.999","fe80::1%eth0","127.1")) {
            rejects(()->VpnReputation.literal(bad),"reject nonliteral "+bad);
        }
        AtomicLong clock=new AtomicLong(-10000); AtomicInteger calls=new AtomicInteger();
        try(var service=new VpnReputation(all,(ip,p)->{calls.incrementAndGet();return response(ip,0);},clock::get)) {
            check(service.screen(VpnReputation.literal("8.8.8.8")).get().outcome()==VpnReputation.Outcome.CLEAR,"first result");
            service.screen(VpnReputation.literal("8.8.8.8")).get(); check(calls.get()==1,"negative monotonic clock cache hit");
            clock.addAndGet(1001); service.screen(VpnReputation.literal("8.8.8.8")).get();check(calls.get()==2,"cache expiry");
            service.screen(VpnReputation.literal("1.1.1.1")).get();service.screen(VpnReputation.literal("9.9.9.9")).get();
            check(service.health().cachedIps()==2,"bounded LRU cache");service.screen(VpnReputation.literal("8.8.8.8")).get();check(calls.get()==5,"old cache entry evicted");
            service.clearCache();check(service.health().cachedIps()==0,"clear cache");
        }
        AtomicInteger localCalls=new AtomicInteger();
        try(var service=new VpnReputation(policy(true,true,true,2000,Set.of("8.8.8.8")),(ip,p)->{localCalls.incrementAndGet();return response(ip,1);})) {
            for(String ip:List.of("8.8.8.8","127.0.0.1","10.0.0.1","192.168.1.1","100.64.0.1","::1")) {
                check(service.screen(VpnReputation.literal(ip)).get().outcome()==VpnReputation.Outcome.CLEAR,"allowlist/local "+ip);
            }
            check(localCalls.get()==0,"no external local lookup");
        }
        AtomicInteger concurrentCalls=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        try(var service=new VpnReputation(all,(ip,p)->{concurrentCalls.incrementAndGet();entered.countDown();release.await(5,TimeUnit.SECONDS);return response(ip,1);})) {
            List<CompletableFuture<VpnReputation.Decision>> futures=new ArrayList<>();
            for(int i=0;i<40;i++)futures.add(service.screen(VpnReputation.literal("8.8.8.8")));
            check(entered.await(3,TimeUnit.SECONDS),"lookup started");release.countDown();
            for(var future:futures)check(future.get(5,TimeUnit.SECONDS).outcome()==VpnReputation.Outcome.BLOCKED,"coalesced result");
            check(concurrentCalls.get()==1,"coalesce 40 joins into one lookup");
        } finally { release.countDown(); }
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/ok",e->{byte[] b=response("8.8.8.8",1).getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
        server.createContext("/rate",e->{e.sendResponseHeaders(429,-1);e.close();});
        server.createContext("/large",e->{byte[] b=new byte[70000];e.sendResponseHeaders(200,b.length);try{e.getResponseBody().write(b);}finally{e.close();}});
        server.createContext("/slow",e->{e.sendResponseHeaders(200,0);try{Thread.sleep(500);e.getResponseBody().write('x');}catch(Exception ignored){}finally{e.close();}});
        server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            String body=new VpnReputation.HttpLookup(URI.create(base+"/ok")).fetch("8.8.8.8",all);
            check(VpnReputation.parse(body,"8.8.8.8",all).outcome()==VpnReputation.Outcome.BLOCKED,"HTTP success");
            rejects(()->new VpnReputation.HttpLookup(URI.create(base+"/rate")).fetch("8.8.8.8",all),"HTTP 429");
            rejects(()->new VpnReputation.HttpLookup(URI.create(base+"/large")).fetch("8.8.8.8",all),"body limit");
            rejects(()->new VpnReputation.HttpLookup(URI.create(base+"/slow")).fetch("8.8.8.8",policy(true,true,true,150,Set.of())),"full body deadline");
        } finally { server.stop(0); }
        check(VpnReputation.HttpLookup.requestUri("8.8.8.8",all).getScheme().equals("https"),"production HTTPS");
        System.out.println("VPN source recovery: "+assertions+" assertions PASS");
    }
}
