package dev.aegisac;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
/** Bounds outstanding network work without blocking the game thread. */
final class BoundedDispatcher {
 private final int capacity;private final AtomicInteger pending=new AtomicInteger();
 BoundedDispatcher(int capacity){if(capacity<1)throw new IllegalArgumentException("capacity");this.capacity=capacity;}
 boolean submit(Executor executor,Runnable action){
  int count;do{count=pending.get();if(count>=capacity)return false;}while(!pending.compareAndSet(count,count+1));
  try{executor.execute(()->{try{action.run();}finally{pending.decrementAndGet();}});return true;}
  catch(RuntimeException rejected){pending.decrementAndGet();return false;}
 }
 int pending(){return pending.get();}
}
