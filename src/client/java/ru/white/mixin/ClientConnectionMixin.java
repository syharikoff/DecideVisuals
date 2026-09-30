package ru.white.mixin;


import ru.white.manager.event_impl.EventPacket;
import ru.white.manager.event_impl.ScreenCloseEvent;
import ru.white.module.impl.utils.consumable.ConsumableHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static ru.white.utils.annotation.IMinecraft.mc;

@Mixin(ClientConnection.class)
public class ClientConnectionMixin {

    @Unique
    private static boolean stackOverflowFix;

    @Inject(method = "handlePacket", at = @At("HEAD"), cancellable = true)
    private static <T extends PacketListener> void triggerReceivePacketEvent(Packet<T> packet, PacketListener listener, CallbackInfo ci) {
        EventPacket event = new EventPacket(packet, EventPacket.Type.RECEIVE);
        event.hook();

        if (event.isCancelled()) {
            ci.cancel();
        }

        if (ConsumableHandler.isEnabled()) {
            ConsumableHandler.handlePacket(packet, ci);
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    public void triggerSendPacketEvent(Packet<?> packet, CallbackInfo ci) {

        if (stackOverflowFix) return;
        EventPacket event = new EventPacket(packet,EventPacket.Type.SEND);
        event.hook();

        if (event.isCancelled()) {
            ci.cancel();
        }

        Packet<?> newPacket = event.getPacket();
        if (newPacket != packet) {
            ci.cancel();

            stackOverflowFix = true;
            mc.getNetworkHandler().sendPacket(newPacket);
            stackOverflowFix = false;
        }
        if (packet instanceof CloseHandledScreenC2SPacket closePacket) {
            MinecraftClient mc = MinecraftClient.getInstance();
            ScreenCloseEvent screenCloseEvent = new ScreenCloseEvent(
                    mc.currentScreen,
                    closePacket.getSyncId()
            );
            screenCloseEvent.hook();
            if (screenCloseEvent.isCancelled()) {
                ci.cancel();
            }
        }
    }
}
