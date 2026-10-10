package dev.aegisac;
import java.util.*;
import ac.grim.grimac.api.event.events.CommandExecuteEvent;
/** Preserve the original event text while attaching the original native action. */
public final class NativeOutputBridge {
 private static final ThreadLocal<String> currentAction=new ThreadLocal<>();
 private static final Map<CommandExecuteEvent,String> actions=Collections.synchronizedMap(new WeakHashMap<>());
 private NativeOutputBridge(){}
 public static void tag(CommandExecuteEvent event,String action){if(event!=null)actions.put(event,action);}
 public static String action(CommandExecuteEvent event){String value=actions.remove(event);return value!=null?value:currentAction.get()!=null?currentAction.get():event.getCommand();}
 public static boolean fireTagged(CommandExecuteEvent.Channel channel,ac.grim.grimac.api.GrimUser user,ac.grim.grimac.api.AbstractCheck check,String verbose,String command,String action){
  String previous=currentAction.get();currentAction.set(action);
  try{return channel.fire(user,check,verbose,command);}finally{if(previous==null)currentAction.remove();else currentAction.set(previous);}
 }

}
