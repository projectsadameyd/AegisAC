package dev.aegisac;

import java.util.*;

/** Pure, bounded server-observable interaction rules. No claims about client mods or camera state. */
final class RemoteInteractionRules {
    record Point(double x,double y,double z) {
        boolean finite(){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
    }
    record Bounds(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
        static Bounds block(int x,int y,int z){return new Bounds(x,y,z,(double)x+1,(double)y+1,(double)z+1);}
        double distance(Point p) {
            double x=Math.max(0,Math.max(minX-p.x,p.x-maxX));
            double y=Math.max(0,Math.max(minY-p.y,p.y-maxY));
            double z=Math.max(0,Math.max(minZ-p.z,p.z-maxZ));
            return Math.sqrt(x*x+y*y+z*z);
        }
        boolean valid(){return Double.isFinite(minX)&&Double.isFinite(minY)&&Double.isFinite(minZ)&&Double.isFinite(maxX)&&Double.isFinite(maxY)&&Double.isFinite(maxZ)&&minX<=maxX&&minY<=maxY&&minZ<=maxZ;}
    }
    enum Kind { BLOCK, ENTITY }
    record Pose(long nanos,UUID world,Point eye,double blockReach,double entityReach,int ping,double tickMillis,boolean eligible) {}
    record Settings(double allowance,double kickExcess,int confirmations,int maxPing,long windowNanos) {
        Settings {
            if(!Double.isFinite(allowance)||allowance<0.5||allowance>5||!Double.isFinite(kickExcess)||kickExcess<1||kickExcess>10
                    ||confirmations<3||confirmations>20||maxPing<50||maxPing>300||windowNanos<1_000_000_000L||windowNanos>10_000_000_000L)
                throw new IllegalArgumentException("Invalid interaction policy settings");
        }
    }
    record Decision(boolean cancel,boolean kick,long epoch,double distance,double limit,int confirmations,String reason) {
        static Decision allow(long epoch,String reason){return new Decision(false,false,epoch,0,0,0,reason);}
    }
    private final ArrayDeque<Pose> poses=new ArrayDeque<>();
    private final EnumMap<Kind,ArrayDeque<Long>> failures=new EnumMap<>(Kind.class);
    private final EnumMap<Kind,Long> lastFailure=new EnumMap<>(Kind.class);
    private long graceUntil,epoch=1;
    private Settings settings;
    RemoteInteractionRules(Settings settings){this.settings=settings;}
    synchronized void configure(Settings next){settings=next;reset(0,0);}
    synchronized void reset(long now,long graceNanos) {
        epoch++;poses.clear();failures.clear();lastFailure.clear();
        graceUntil=Math.max(graceUntil,now+Math.min(10_000_000_000L,Math.max(0,graceNanos)));
    }
    synchronized void sample(Pose pose) {
        if(!pose.eye.finite()||!Double.isFinite(pose.blockReach)||!Double.isFinite(pose.entityReach))return;
        Pose prior=poses.peekLast();
        if(prior!=null&&(!prior.world.equals(pose.world)||pose.nanos<prior.nanos))reset(pose.nanos,2_000_000_000L);
        if(!pose.eligible||pose.ping<0||pose.ping>settings.maxPing||pose.tickMillis>75||pose.tickMillis<0) {
            failures.clear();lastFailure.clear();
        }
        poses.addLast(pose);
        while(poses.size()>16||(!poses.isEmpty()&&pose.nanos-poses.peekFirst().nanos>800_000_000L))poses.removeFirst();
    }
    synchronized Decision evaluate(Kind kind,UUID world,Bounds target,long now) {
        Pose latest=poses.peekLast();
        if(latest==null||now<latest.nanos||now-latest.nanos>150_000_000L||now<graceUntil||!latest.eligible
                ||latest.ping<0||latest.ping>settings.maxPing||latest.tickMillis>75||latest.tickMillis<0||!latest.world.equals(world)||!target.valid())
            return uncertain("latency, lifecycle or snapshot uncertainty");
        double minimumExcess=Double.POSITIVE_INFINITY,bestDistance=0,bestLimit=0;
        for(Pose pose:poses) {
            if(!pose.world.equals(world)||now-pose.nanos>800_000_000L||!pose.eligible)continue;
            double reach=kind==Kind.BLOCK?pose.blockReach:pose.entityReach;
            double d=target.distance(pose.eye),limit=reach+settings.allowance;
            if(d-limit<minimumExcess){minimumExcess=d-limit;bestDistance=d;bestLimit=limit;}
        }
        if(!Double.isFinite(minimumExcess))return uncertain("no matching history");
        if(minimumExcess<=0) {
            failures.remove(kind);lastFailure.remove(kind);
            return Decision.allow(epoch,"within retained server reach");
        }
        int count=0;
        if(minimumExcess>=settings.kickExcess) {
            ArrayDeque<Long> evidence=failures.computeIfAbsent(kind,key->new ArrayDeque<>());
            while(!evidence.isEmpty()&&now-evidence.peekFirst()>settings.windowNanos)evidence.removeFirst();
            long last=lastFailure.getOrDefault(kind,Long.MIN_VALUE/2);
            // Main/off-hand duplicates, packet bursts and START/FINISH pairs cannot become separate proofs.
            if(now-last>=250_000_000L) {
                evidence.addLast(now);lastFailure.put(kind,now);
                while(evidence.size()>settings.confirmations)evidence.removeFirst();
            }
            count=evidence.size();
        }else{failures.remove(kind);lastFailure.remove(kind);}
        return new Decision(true,count>=settings.confirmations,epoch,bestDistance,bestLimit,count,"interaction outside every retained server reach envelope");
    }
    private Decision uncertain(String reason){failures.clear();lastFailure.clear();return Decision.allow(epoch,reason);}
    synchronized boolean current(long expected,long now) {
        Pose p=poses.peekLast();return epoch==expected&&p!=null&&p.eligible&&p.ping>=0&&p.ping<=settings.maxPing&&p.tickMillis<=75
                &&now>=graceUntil&&now>=p.nanos&&now-p.nanos<=150_000_000L;
    }
    synchronized List<Pose> history(){return List.copyOf(poses);}
    synchronized int buffered(){return failures.values().stream().mapToInt(ArrayDeque::size).sum();}
    synchronized long epoch(){return epoch;}
}
