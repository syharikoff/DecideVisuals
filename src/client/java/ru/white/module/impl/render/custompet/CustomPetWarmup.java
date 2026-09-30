package ru.white.module.impl.render.custompet;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.GeckoLibResources;

public final class CustomPetWarmup {
    private static final Identifier TEXTURE = Identifier.of("wvisual", "textures/entity/frog/custom_pet.png");
    private static final Identifier MODEL = Identifier.of("wvisual", "frog/custom_pet");
    private static boolean warmedUp;

    private CustomPetWarmup() {
    }

    public static void warmup() {
        if (warmedUp) {
            return;
        }
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null) {
            return;
        }
        try {
            if (minecraft.getTextureManager() != null) {
                minecraft.getTextureManager().getTexture(TEXTURE);
            }
        } catch (Throwable ignored) {
        }
        try {
            GeckoLibResources.getBakedModels().getModel(MODEL);
        } catch (Throwable ignored) {
        }
        warmedUp = true;
    }
}