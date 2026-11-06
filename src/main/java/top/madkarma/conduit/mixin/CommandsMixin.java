package top.madkarma.conduit.mixin;

import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.madkarma.conduit.events.GameEvents;

/** Player, console, command block and gRPC commands all pass through performCommand. */
@Mixin(Commands.class)
public abstract class CommandsMixin {
    @Inject(method = "performCommand", at = @At("HEAD"))
    private void conduit$onCommand(
            ParseResults<CommandSourceStack> command, String commandString, CallbackInfo info) {
        GameEvents.onCommand(command.getContext().getSource().getTextName(), commandString);
    }
}
