package ru.white.mixin;


import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.white.module.impl.render.ModelCollapse;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    /** Model Collapse: прячем оригинальную модель, пока на её месте рисуются осколки. */
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void hideCollapsedModel(Entity entity, Frustum frustum,
                                     double camX, double camY, double camZ,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof LivingEntity living && ModelCollapse.shouldHideEntity(living)) {
            cir.setReturnValue(false);
        }
    }

}
