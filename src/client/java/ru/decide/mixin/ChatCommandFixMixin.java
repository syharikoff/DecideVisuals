package ru.decide.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.decide.module.impl.utils.AutoFixCommand;

/**
 * Auto Fix Command для команд, которые уходят в сеть не из чата:
 * бинды (например Coord Invite), чат-синьки, макросы.
 * <p>
 * Переотправка вместо правки аргумента — в этом билде
 * {@code @ModifyVariable} молча не применяется (см. SoundManagerMixin),
 * а повторный вызов безопасен: исправленная команда повторно не меняется.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class ChatCommandFixMixin {

    @Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true)
    private void decide$fixChatCommand(String command, CallbackInfo ci) {
        String fixed = AutoFixCommand.processServerCommand(command);

        if (!fixed.equals(command)) {
            ci.cancel();
            ((ClientPlayNetworkHandler) (Object) this).sendChatCommand(fixed);
        }
    }
}