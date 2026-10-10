import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.commons.*;
import jdk.internal.org.objectweb.asm.tree.*;
public class RuntimePatcher {
 public static void main(String[] args)throws Exception {
  byte[] bytes=Files.readAllBytes(Path.of(args[0]));ClassReader reader=new ClassReader(bytes);
  ClassNode node=new ClassNode();reader.accept(node,0);
  if(node.name.equals("ac/grim/grimac/platform/bukkit/GrimACBukkitLoaderPlugin"))node.access&=~Opcodes.ACC_FINAL;
  if(node.name.equals("net/donutac/anticheat/DonutAntiCheat")){
   node.superName="dev/aegisac/SensorPlugin";
   for(MethodNode m:node.methods)for(AbstractInsnNode i:m.instructions)if(i instanceof MethodInsnNode call&&call.owner.equals("org/bukkit/plugin/java/JavaPlugin"))call.owner="dev/aegisac/SensorPlugin";
  }
  if(node.name.equals("ac/grim/grimac/platform/bukkit/initables/BukkitBStats"))for(MethodNode m:node.methods)if(m.name.equals("start")&&m.desc.equals("()V")){m.instructions.clear();m.instructions.add(new InsnNode(Opcodes.RETURN));m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();}
  if(node.name.equals("ac/grim/grimac/manager/AlertManagerImpl"))for(MethodNode m:node.methods){
   if(m.name.equals("sendAlert")||m.name.equals("sendVerbose")||m.name.equals("sendBrand")){m.instructions.clear();m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/util/Collections","emptySet","()Ljava/util/Set;",false));m.instructions.add(new InsnNode(Opcodes.ARETURN));m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();}
   if(m.name.equals("hasVerboseListeners")){m.instructions.clear();m.instructions.add(new InsnNode(Opcodes.ICONST_0));m.instructions.add(new InsnNode(Opcodes.IRETURN));m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();}
  }
  if(node.name.equals("ac/grim/grimac/manager/PunishmentManager")){int tagged=0;
   for(MethodNode m:node.methods)if(m.name.equals("handleAlert"))for(AbstractInsnNode i:m.instructions.toArray())
    if(i instanceof MethodInsnNode call&&call.owner.equals("ac/grim/grimac/api/event/events/CommandExecuteEvent$Channel")&&call.name.equals("fire")&&call.desc.equals("(Lac/grim/grimac/api/GrimUser;Lac/grim/grimac/api/AbstractCheck;Ljava/lang/String;Ljava/lang/String;)Z")){
     InsnList tag=new InsnList();tag.add(new VarInsnNode(Opcodes.ALOAD,10));tag.add(new FieldInsnNode(Opcodes.GETFIELD,"ac/grim/grimac/manager/ParsedCommand","command","Ljava/lang/String;"));m.instructions.insertBefore(i,tag);
     call.setOpcode(Opcodes.INVOKESTATIC);call.owner="dev/aegisac/NativeOutputBridge";call.name="fireTagged";call.itf=false;call.desc="(Lac/grim/grimac/api/event/events/CommandExecuteEvent$Channel;Lac/grim/grimac/api/GrimUser;Lac/grim/grimac/api/AbstractCheck;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z";tagged++;
    }
   if(tagged!=1)throw new IllegalStateException("Expected one native command channel, got "+tagged);
  }
  ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
  node.accept(new ClassRemapper(writer,new Remapper(){public String map(String name){
   if(name.startsWith("aegis/grim/"))return "dev/aegisac/"+name.substring(11);
   if(name.equals("net/donutac/anticheat/integration/AegisEvidenceBridge"))return "dev/aegisac/DonutEvidenceBridge";
   return name;
  }}));
  Path dest=Path.of(args[1]);Files.createDirectories(dest.getParent());Files.write(dest,writer.toByteArray());
 }
}