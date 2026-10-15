package dev.aegisac;

import ac.grim.grimac.api.GrimUser;
import ac.grim.grimac.checks.Check;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/** Exercises actual action routing, including the private dispatch path; no mocked safety predicate. */
public final class R17TimingSafetyRegressionTest {
    private static int assertions;
    private static void require(boolean b,String label){assertions++;if(!b)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception {
        Path folder=Files.createTempDirectory("r17-timing-policy-");UUID id=UUID.randomUUID();
        AegisGrimAddon addon=NativeActionRegressionTest.addon(folder,Logger.getAnonymousLogger());
        GrimUser user=NativeActionRegressionTest.proxy(GrimUser.class,(p,m,a)->switch(m.getName()){case "getUniqueId"->id;case "getName"->"TimingFixture";default->NativeActionRegressionTest.empty(m);});
        var direct=AegisGrimAddon.class.getDeclaredMethod("nativeAction",UUID.class,String.class,String.class,String.class,String.class,Check.class);direct.setAccessible(true);
        for(String name:List.of("Timer","TimerA","TimerLimit","TickTimer","NegativeTimer","VehicleTimer","tIcKtImEr")) {
            Check check=NativeActionRegressionTest.check(name);
            NativeActionRegressionTest.action(addon,user,check,"[aegis]");require(addon.pendingReviews()==0,name+" command event cannot enqueue a ban");
            direct.invoke(addon,id,"TimingFixture","AegisAC",name,"test timing discrepancy",check);require(addon.pendingReviews()==0,name+" direct routing cannot enqueue a ban");
        }
        require(addon.activeBans().isEmpty(),"no timing ban persisted");require(addon.history().size()==0,"no confirmed-cheating record fabricated");
        NativeActionRegressionTest.stop(addon);
        try(var paths=Files.walk(folder)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
        System.out.println("PASS: "+assertions+" actual timing sanction routing and persistence assertions");
    }
}
