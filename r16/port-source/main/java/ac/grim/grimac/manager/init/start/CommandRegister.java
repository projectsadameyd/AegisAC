package ac.grim.grimac.manager.init.start;

import ac.grim.grimac.platform.api.command.CommandService;

public record CommandRegister(CommandService service) implements StartableInitable {
   @Override
   public void start() {
   }
}
