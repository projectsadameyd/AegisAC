package dev.aegisac;

import java.util.*;
import java.util.concurrent.*;

/** Observable action traces: legitimate histories, lifecycle races, duplicates and sustained abuse. */
public final class RemoteInteractionRegressionTest {
    private static int checks;
    private static final UUID WORLD=UUID.fromString("71701735-eb46-42f5-9f06-fe9b994fc4b3");
    private static final RemoteInteractionRules.Settings POLICY=new RemoteInteractionRules.Settings(1.25,2,3,200,4_000_000_000L);
    private static void require(boolean b,String label){checks++;if(!b)throw new AssertionError(label);}
    private static RemoteInteractionRules fresh(long now,double x,double blockReach,int ping,boolean eligible) {
        RemoteInteractionRules r=new RemoteInteractionRules(POLICY);sample(r,now,x,blockReach,ping,50,eligible);return r;
    }
    private static void sample(RemoteInteractionRules r,long now,double x,double reach,int ping,double tick,boolean eligible){r.sample(new RemoteInteractionRules.Pose(now,WORLD,new RemoteInteractionRules.Point(x,65.62,0),reach,3,ping,tick,eligible));}
    public static void main(String[] args)throws Exception {
        long now=10_000_000_000L;var remote=RemoteInteractionRules.Bounds.block(20,64,0);
        var vanilla=fresh(now,0,4.5,50,true);
        for(int x=-4;x<=4;x++)for(int y=63;y<=67;y++) {
            var block=RemoteInteractionRules.Bounds.block(x,y,0);var d=vanilla.evaluate(RemoteInteractionRules.Kind.BLOCK,WORLD,block,now);
            if(block.distance(new RemoteInteractionRules.Point(0,65.62,0))<=5.75)require(!d.cancel(),"legal block interaction "+x+"/"+y);
        }
        var reach=fresh(now,0,20,50,true);for(int i=0;i<10;i++)require(!reach.evaluate(RemoteInteractionRules.Kind.BLOCK,WORLD,remote,now).cancel(),"custom reach attribute respected");
        var historical=fresh(now-500_000_000,18,4.5,150,true);sample(historical,now,0,4.5,150,50,true);require(!historical.evaluate(RemoteInteractionRules.Kind.BLOCK,WORLD,remote,now).cancel(),"legal recent position preserved for latency");
        var stale=fresh(now-200_000_000,0,4.5,50,true);require(!stale.evaluate(RemoteInteractionRules.Kind.BLOCK,WORLD,remote,now).cancel(),"stale snapshot fails open");
        var ping=fresh(now,0,4.5,300,true);require(!ping.evaluate(RemoteInteractionRules.Kind.BLOCK,WORLD,remote,now).cancel(),"high ping is not evidence");
        var lag=fresh(now,0,4.5,50,true);sample(lag,now,0,4.5,50,150,true);require(!lag.evaluate(RemoteInteractionRules.Kind.BLOCK,WORLD,remote,now).cancel(),"server stalls not evidence");
        var bypass=fresh(now,0,4.5,50,false);require(!bypass.evaluate(RemoteInteractionRules.Kind.BLOCK,WORLD,remote,now).cancel(),"explicit bypass respected");
        var teleport=fresh(now,0,4.5,50,true);teleport.reset(now,2_500_000_000L);sample(teleport,now+1_000_000_000L,0,4.5,50,50,true);require(!teleport.evaluate(RemoteInteractionRules.Kind.BLOCK,WORLD,remote,now+1_000_000_000L).cancel(),"teleport grace");
        var wrongWorld=fresh(now,0,4.5,50,true);require(!wrongWorld.evaluate(RemoteInteractionRules.Kind.BLOCK,UUID.randomUUID(),remote,now).cancel(),"world identity isolation");
        var duplicate=fresh(now,0,4.5,50,true);for(int i=0;i<200;i++){var d=duplicate.evaluate(RemoteInteractionRules.Kind.BLOCK,WORLD,remote,now+i*100_000L);require(d.cancel()&&!d.kick(),"one packet burst cannot kick");}
        var sustained=fresh(now,0,4.5,50,true);RemoteInteractionRules.Decision last=null;
        for(int i=0;i<3;i++){long t=now+i*300_000_000L;sample(sustained,t,0,4.5,50,50,true);last=sustained.evaluate(RemoteInteractionRules.Kind.BLOCK,WORLD,remote,t);require(last.cancel(),"remote action cancelled");require(last.kick()==(i==2),"three spaced proofs required");}
        long kickEpoch=last.epoch();require(sustained.current(kickEpoch,now+600_000_000L),"pending action valid before teleport");sustained.reset(now+600_000_000L,2_500_000_000L);require(!sustained.current(kickEpoch,now+600_000_000L),"teleport invalidates queued kick");
        var expiry=fresh(now,0,4.5,50,true);expiry.evaluate(RemoteInteractionRules.Kind.BLOCK,WORLD,remote,now);
        sample(expiry,now+5_000_000_000L,0,4.5,50,50,true);var expired=expiry.evaluate(RemoteInteractionRules.Kind.BLOCK,WORLD,remote,now+5_000_000_000L);require(!expired.kick()&&expired.confirmations()==1,"confirmation evidence expires");
        var recovery=fresh(now,0,4.5,50,true);recovery.evaluate(RemoteInteractionRules.Kind.BLOCK,WORLD,remote,now);sample(recovery,now+300_000_000L,0,4.5,50,50,true);recovery.evaluate(RemoteInteractionRules.Kind.BLOCK,WORLD,RemoteInteractionRules.Bounds.block(2,64,0),now+300_000_000L);sample(recovery,now+600_000_000L,0,4.5,50,50,true);require(recovery.evaluate(RemoteInteractionRules.Kind.BLOCK,WORLD,remote,now+600_000_000L).confirmations()==1,"legal interaction clears old proofs");
        var entities=fresh(now,0,4.5,50,true);require(!entities.evaluate(RemoteInteractionRules.Kind.ENTITY,WORLD,new RemoteInteractionRules.Bounds(2.9,64,-.3,3.5,65.8,.3),now).cancel(),"legal attack reach");require(entities.evaluate(RemoteInteractionRules.Kind.ENTITY,WORLD,new RemoteInteractionRules.Bounds(15,64,0,16,66,1),now).cancel(),"remote entity reach blocked");
        var negative=RemoteInteractionRules.Bounds.block(Integer.MAX_VALUE,64,0);require(negative.valid()&&negative.maxX()>negative.minX(),"malformed boundary cannot overflow block bounds");
        require(!entities.evaluate(RemoteInteractionRules.Kind.ENTITY,WORLD,new RemoteInteractionRules.Bounds(Double.NaN,0,0,1,1,1),now).cancel(),"invalid geometry is uncertainty");
        var bounded=fresh(now,0,4.5,50,true);for(int i=0;i<1000;i++)sample(bounded,now+i*10_000_000L,0,4.5,50,50,true);require(bounded.history().size()<=16,"bounded pose history");
        for(int i=0;i<200;i++){long t=now+10_000_000_000L+i*300_000_000L;sample(bounded,t,0,4.5,50,50,true);bounded.evaluate(RemoteInteractionRules.Kind.BLOCK,WORLD,remote,t);}require(bounded.buffered()<=6,"bounded evidence windows");
        var passive=fresh(now,0,4.5,50,true);for(int i=0;i<200;i++)sample(passive,now+i*50_000_000L,0,4.5,50,50,true);require(passive.buffered()==0,"stationary camera-only behaviour produces no fabricated evidence");
        var parallel=fresh(now,0,4.5,50,true);ExecutorService workers=Executors.newFixedThreadPool(4);List<Future<?>> tasks=new ArrayList<>();
        for(int j=0;j<4;j++)tasks.add(workers.submit(()->{for(int i=0;i<1000;i++){parallel.evaluate(RemoteInteractionRules.Kind.BLOCK,WORLD,remote,now);parallel.history();}}));for(var task:tasks)task.get(10,TimeUnit.SECONDS);workers.shutdown();require(parallel.buffered()==1,"concurrent duplicate packet ordering");
        System.out.println("PASS: "+checks+" remote interaction, latency, lifecycle, reach, duplicate and bounded-state assertions");
    }
}
