package ru.white.mixin;

import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.white.utils.render.shader.ShaderStore;

/**
 * Перехватывает выдачу исходника шейдера движком. Компилятор пайплайнов берёт GLSL
 * через {@code ShaderLoader$Cache.getSource(Identifier, ShaderType)} (ShaderSourceGetter),
 * поэтому перехват именно здесь — на публичном ShaderLoader.getSource этот путь не идёт.
 *
 * Для шейдеров в namespace "client" исходник берётся из зашифрованных Java-констант
 * (ShaderStore), а не из файлов ресурс-пака. Все pipeline-классы продолжают использовать
 * Identifier.of("client", "core/...") без изменений.
 */
@Mixin(ShaderLoader.Cache.class)
public class ShaderLoaderMixin {

    @Inject(method = "getSource", at = @At("HEAD"), cancellable = true)
    private void white$provideEmbeddedSource(Identifier id, ShaderType type,
                                             CallbackInfoReturnable<String> cir) {
        String source = ShaderStore.getSource(id, type);
        if (source != null) {
            cir.setReturnValue(source);
        }
    }
}
