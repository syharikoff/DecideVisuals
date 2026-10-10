package ru.decide.mixin.velotune;

import ru.decide.optimization.velotune.VeloTuneManager;
import ru.decide.optimization.velotune.hud.ScreenFramebufferCache;
import ru.decide.optimization.velotune.input.RawInputManager;
import ru.decide.optimization.velotune.input.WindowsRawInputBuffer;
import ru.decide.optimization.velotune.perf.TextRefreshThrottle;
import ru.decide.optimization.velotune.perf.VeloTuneConfig;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.util.Window;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWDropCallback;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(value=EnvType.CLIENT)
@Mixin(value={Mouse.class})
abstract class RawMouseMixin {
    @Shadow
    @Final
    private MinecraftClient client;
    @Shadow
    private double cursorDeltaX;
    @Shadow
    private double cursorDeltaY;
    @Unique
    private double velotune$heldMouseDeltaX;
    @Unique
    private double velotune$heldMouseDeltaY;
    @Unique
    private long velotune$lastMouseUpdateNanos;
    @Unique
    private boolean velotune$mouseRateInitialized;

    @Shadow
    protected abstract void onCursorPos(long var1, double var3, double var5);

    @Shadow
    protected abstract void onMouseButton(long var1, MouseInput var3, int var4);

    @Shadow
    protected abstract void onMouseScroll(long var1, double var3, double var5);

    @Shadow
    protected abstract void onFilesDropped(long var1, List<Path> var3, int var4);

    @Inject(method={"setup"}, at={@At(value="HEAD")}, cancellable=true)
    private void velotune$setupRawInput(Window window, CallbackInfo ci) {
        if (FabricLoader.getInstance().isModLoaded("rawinputbuffer")) {
            return;
        }
        long handle = window.getHandle();
        WindowsRawInputBuffer buffer = RawInputManager.buffer();
        if (!buffer.initialize(handle, (callbackWindow, button, action, modifiers) -> this.onMouseButton(callbackWindow, new MouseInput(button, modifiers), action), this::onMouseScroll)) {
            return;
        }
        this.velotune$installGlfwCallbacks(handle);
        VeloTuneManager.LOGGER.info("Windows raw mouse input buffer initialized");
        ci.cancel();
    }

    @Inject(method={"updateMouse"}, at={@At(value="HEAD")})
    private void velotune$consumeRawInput(double dlt, CallbackInfo ci) {
        boolean cameraActive = this.client.isWindowFocused() && this.client.currentScreen == null && this.velotune$rawRequested();
        WindowsRawInputBuffer buffer = RawInputManager.buffer();
        if (buffer.isRunning()) {
            boolean rawWanted = this.velotune$rawRequested() && cameraActive;
            Window window = this.client.getWindow();
            buffer.updateState(this.client.isWindowFocused(), rawWanted, window.getX() + window.getWidth() / 2, window.getY() + window.getHeight() / 2);
            boolean rawActive = rawWanted && buffer.isCapturingRawInput();
            buffer.flushEvents(rawActive);
            if (rawActive) {
                this.cursorDeltaX += buffer.pollDeltaX();
                this.cursorDeltaY += buffer.pollDeltaY();
            } else {
                buffer.clearPending();
            }
        }
        if (!cameraActive) {
            this.velotune$heldMouseDeltaX = 0.0;
            this.velotune$heldMouseDeltaY = 0.0;
            this.velotune$mouseRateInitialized = false;
        }
    }

    @Inject(method={"updateMouse"}, at={@At(value="HEAD")})
    private void velotune$limitMouseUpdateRate(double dlt, CallbackInfo ci) {
        int rate;
        VeloTuneConfig config = VeloTuneManager.config();
        int n = rate = config.enabled && config.extreme.enabled ? config.extreme.mouseUpdatesPerSecond : 0;
        if (rate <= 0) {
            this.cursorDeltaX += this.velotune$heldMouseDeltaX;
            this.cursorDeltaY += this.velotune$heldMouseDeltaY;
            this.velotune$heldMouseDeltaX = 0.0;
            this.velotune$heldMouseDeltaY = 0.0;
            this.velotune$mouseRateInitialized = false;
            return;
        }
        long now = System.nanoTime();
        if (TextRefreshThrottle.shouldRefresh(this.velotune$mouseRateInitialized, now - this.velotune$lastMouseUpdateNanos, rate)) {
            this.cursorDeltaX += this.velotune$heldMouseDeltaX;
            this.cursorDeltaY += this.velotune$heldMouseDeltaY;
            this.velotune$heldMouseDeltaX = 0.0;
            this.velotune$heldMouseDeltaY = 0.0;
            this.velotune$lastMouseUpdateNanos = now;
            this.velotune$mouseRateInitialized = true;
        } else {
            this.velotune$heldMouseDeltaX += this.cursorDeltaX;
            this.velotune$heldMouseDeltaY += this.cursorDeltaY;
            this.cursorDeltaX = 0.0;
            this.cursorDeltaY = 0.0;
        }
    }

    @Inject(method={"lockCursor"}, at={@At(value="HEAD")})
    private void velotune$lockRawInput(CallbackInfo ci) {
        WindowsRawInputBuffer buffer = RawInputManager.buffer();
        if (buffer.isRunning()) {
            buffer.setExclusive(this.velotune$rawRequested() && this.client.isWindowFocused());
        }
    }

    @Inject(method={"unlockCursor"}, at={@At(value="HEAD")})
    private void velotune$unlockRawInput(CallbackInfo ci) {
        WindowsRawInputBuffer buffer = RawInputManager.buffer();
        if (buffer.isRunning()) {
            buffer.setExclusive(false);
        }
    }

    @Inject(method={"onMouseButton(JLnet/minecraft/client/input/MouseInput;I)V"}, at={@At(value="HEAD")})
    private void velotune$invalidateScreenOnClick(long window, MouseInput input, int action, CallbackInfo ci) {
        if (this.client.currentScreen != null) {
            ScreenFramebufferCache.invalidate();
        }
    }

    @Inject(method={"onMouseScroll(JDD)V"}, at={@At(value="HEAD")})
    private void velotune$invalidateScreenOnScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (this.client.currentScreen != null) {
            ScreenFramebufferCache.invalidate();
        }
    }

    @Unique
    private boolean velotune$rawRequested() {
        VeloTuneConfig config = VeloTuneManager.config();
        return config.enabled && config.extreme.enabled && config.extreme.rawInputBuffer;
    }

    @Unique
    private boolean velotune$forwardGlfwInput() {
        return !this.velotune$rawRequested() || this.client.currentScreen != null || !RawInputManager.buffer().isCapturingRawInput();
    }

    @Unique
    private void velotune$installGlfwCallbacks(long window) {
        GLFW.glfwSetCursorPosCallback((long)window, (callbackWindow, x, y) -> {
            if (this.velotune$forwardGlfwInput()) {
                this.client.execute(() -> this.onCursorPos(callbackWindow, x, y));
            }
        });
        GLFW.glfwSetMouseButtonCallback((long)window, (callbackWindow, button, action, modifiers) -> {
            if (this.velotune$forwardGlfwInput()) {
                this.client.execute(() -> this.onMouseButton(callbackWindow, new MouseInput(button, modifiers), action));
            }
        });
        GLFW.glfwSetScrollCallback((long)window, (callbackWindow, horizontal, vertical) -> {
            if (this.velotune$forwardGlfwInput()) {
                this.client.execute(() -> this.onMouseScroll(callbackWindow, horizontal, vertical));
            }
        });
        GLFW.glfwSetDropCallback((long)window, (callbackWindow, count, names) -> {
            ArrayList<Path> paths = new ArrayList<Path>(count);
            int invalidPaths = 0;
            for (int index = 0; index < count; ++index) {
                String name = GLFWDropCallback.getName((long)names, (int)index);
                try {
                    paths.add(Paths.get(name, new String[0]));
                    continue;
                }
                catch (InvalidPathException error) {
                    ++invalidPaths;
                    VeloTuneManager.LOGGER.error("Failed to parse dropped path '{}'", (Object)name, (Object)error);
                }
            }
            if (!paths.isEmpty()) {
                int finalInvalidPaths = invalidPaths;
                this.client.execute(() -> this.onFilesDropped(callbackWindow, paths, finalInvalidPaths));
            }
        });
    }
}
