package dev.aegisac;
import java.util.*;import java.util.concurrent.ConcurrentHashMap;import java.util.function.Consumer;
final class SanctionDispatcher {
 private final Map<UUID,Object> pending=new ConcurrentHashMap<>();
 boolean submit(UUID id,Consumer<Runnable> scheduler,Runnable action){if(id==null)return false;Object token=new Object();if(pending.putIfAbsent(id,token)!=null)return false;try{scheduler.accept(()->{try{if(pending.get(id)==token)action.run();}finally{pending.remove(id,token);}});return true;}catch(RuntimeException failure){pending.remove(id,token);throw failure;}}
 void forget(UUID id){pending.remove(id);}void clear(){pending.clear();}int pending(){return pending.size();}
}
