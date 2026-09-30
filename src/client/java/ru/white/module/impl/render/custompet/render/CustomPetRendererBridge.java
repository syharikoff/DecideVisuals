package ru.white.module.impl.render.custompet.render;

import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.MapRenderer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.equipment.EquipmentModelLoader;
import net.minecraft.client.render.entity.model.LoadedEntityModels;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.texture.AtlasManager;
import net.minecraft.client.texture.PlayerSkinCache;
import net.minecraft.entity.Entity;
import ru.white.module.impl.render.custompet.entity.CustomPetEntity;

public final class CustomPetRendererBridge {
    private static MinecraftClient minecraft;
    private static EntityRenderManager dispatcher;
    private static BlockRenderManager blockRenderManager;
    private static ItemModelManager itemModelManager;
    private static MapRenderer mapRenderer;
    private static TextRenderer font;
    private static Supplier<LoadedEntityModels> modelSetSupplier;
    private static EquipmentModelLoader equipmentModelLoader;
    private static AtlasManager atlasManager;
    private static PlayerSkinCache playerSkinCache;
    private static EntityRenderer<? super CustomPetEntity, ?> renderer;

    private CustomPetRendererBridge() {
    }

    public static void bootstrap(
            EntityRenderManager entityRenderManager,
            MinecraftClient minecraftClient,
            BlockRenderManager blockRenderManager,
            ItemModelManager itemModelManager,
            MapRenderer mapRenderer,
            AtlasManager atlasManager,
            TextRenderer textRenderer,
            Supplier<LoadedEntityModels> supplier,
            EquipmentModelLoader equipmentModelLoader,
            PlayerSkinCache playerSkinCache
    ) {
        dispatcher = entityRenderManager;
        minecraft = minecraftClient;
        CustomPetRendererBridge.blockRenderManager = blockRenderManager;
        CustomPetRendererBridge.itemModelManager = itemModelManager;
        CustomPetRendererBridge.mapRenderer = mapRenderer;
        CustomPetRendererBridge.atlasManager = atlasManager;
        font = textRenderer;
        modelSetSupplier = supplier;
        CustomPetRendererBridge.equipmentModelLoader = equipmentModelLoader;
        CustomPetRendererBridge.playerSkinCache = playerSkinCache;
        renderer = null;
    }

    public static void reload() {
        renderer = null;
    }

    private static EntityRenderer<? super CustomPetEntity, ?> getOrCreateRenderer() {
        if (renderer == null) {
            EntityRendererFactory.Context context = new EntityRendererFactory.Context(
                    dispatcher,
                    itemModelManager,
                    mapRenderer,
                    blockRenderManager,
                    minecraft.getResourceManager(),
                    modelSetSupplier.get(),
                    equipmentModelLoader,
                    atlasManager,
                    font,
                    playerSkinCache
            );
            renderer = new CustomPetRenderer(context);
        }

        return renderer;
    }

    public static <S extends EntityRenderState> EntityRenderer<?, ? super S> getCustomRenderer(S s) {
        if (dispatcher == null || minecraft == null) {
            return null;
        } else {
            return s instanceof CustomPetRenderState ? (EntityRenderer<?, ? super S>) (EntityRenderer<?, ?>) getOrCreateRenderer() : null;
        }
    }

    public static CustomPetRenderer getPetRenderer() {
        if (dispatcher == null || minecraft == null) {
            return null;
        } else {
            return (CustomPetRenderer) getOrCreateRenderer();
        }
    }

    public static void renderBlockAsEntity(net.minecraft.block.BlockState state, net.minecraft.client.util.math.MatrixStack matrices, net.minecraft.client.render.VertexConsumerProvider provider, int light, int overlay) {
        if (blockRenderManager == null) {
            return;
        }
        blockRenderManager.renderBlockAsEntity(state, matrices, provider, light, overlay);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Entity> EntityRenderer<? super T, ?> getCustomRenderer(T t) {
        if (dispatcher == null || minecraft == null) {
            return null;
        } else {
            if (t instanceof CustomPetEntity) {
                return (EntityRenderer<? super T, ?>) getOrCreateRenderer();
            }
            return null;
        }
    }
}