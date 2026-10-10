package ru.decide.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {

    @Mutable
    @Accessor("session")
    void setSession(Session session);

    /**
     * Приватный {@code doAttack()} (в 1.21.4+ это бывший {@code startAttack}).
     * Нужен SpearHelper, чтобы ударить копьём из слота хотбара.
     */
    @Invoker("doAttack")
    boolean decide$doAttack();
}
