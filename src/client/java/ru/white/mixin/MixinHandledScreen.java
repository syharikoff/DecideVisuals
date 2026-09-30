package ru.white.mixin;


import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.white.Client;
import ru.white.inventorypreset.InventoryPresetManager;
import ru.white.module.impl.player.LockSlot;
import ru.white.module.impl.utils.AutoSwapModule;
import ru.white.module.impl.utils.ItemScroller;
import ru.white.screen.InventoryPresetScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(HandledScreen.class)
public abstract class MixinHandledScreen<T extends ScreenHandler> extends Screen {

    @Shadow
    @Final
    protected T handler;
    @Shadow @Nullable
    protected Slot focusedSlot;

    @Shadow protected abstract void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType);

    private long time = 0;

    @Unique private static final int PRESET_BUTTON_SIZE = 18;
    @Unique private ButtonWidget presetButton;

    protected MixinHandledScreen(Text title) {
        super(title);
    }

    /** Кнопка-сундук над инвентарём открывает менеджер пресетов. */
    @Inject(method = "init", at = @At("TAIL"))
    private void initPresetButton(CallbackInfo ci) {
        if (!((Object) this instanceof InventoryScreen) || client == null) return;

        HandledScreenAccessor accessor = (HandledScreenAccessor) this;
        presetButton = ButtonWidget.builder(Text.empty(), button ->
                        client.setScreen(new InventoryPresetScreen((Screen) (Object) this)))
                .dimensions(accessor.getScreenX() + 151, accessor.getScreenY() - 20,
                        PRESET_BUTTON_SIZE, PRESET_BUTTON_SIZE)
                .build();
        addDrawableChild(presetButton);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void clickPresetUi(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof InventoryScreen) || client == null || client.player == null
                || Client.get() == null) return;

        AutoSwapModule autoSwap = Client.get().moduleManager().get(AutoSwapModule.class);
        if (autoSwap != null && autoSwap.isPicking() && click.button() == 0
                && focusedSlot != null && focusedSlot.hasStack()) {
            ItemStack stack = focusedSlot.getStack().copy();
            client.interactionManager.clickSlot(
                    client.player.currentScreenHandler.syncId,
                    focusedSlot.id, 40, SlotActionType.SWAP, client.player);
            autoSwap.finishPick(stack);
            client.setScreen(null);
            cir.setReturnValue(true);
            return;
        }

        if (click.button() != 0) return;

        InventoryPresetManager manager = Client.get().inventoryPresetManager();
        if (manager == null || manager.activePreset().isEmpty()) return;

        HandledScreenAccessor accessor = (HandledScreenAccessor) this;
        int guiX = accessor.getScreenX();
        int guiY = accessor.getScreenY();
        double mouseX = click.x();
        double mouseY = click.y();

        Map<String, InventoryPresetManager.MissingItem> missing = manager.missingItems();
        List<InventoryPresetManager.MissingItem> missingItems = new ArrayList<>(missing.values());
        int visibleRows = Math.min(8, Math.max(1, missingItems.size()));
        int missingScroll = manager.missingScroll(missingItems.size(), visibleRows);
        int panelX = guiX + 182;
        int panelY = guiY + 8;
        int panelW = 118;
        int rowH = 21;
        int rowY = panelY + 25;

        for (int index = missingScroll; index < Math.min(missingItems.size(), missingScroll + visibleRows); index++) {
            InventoryPresetManager.MissingItem item = missingItems.get(index);
            if (inside(mouseX, mouseY, panelX + 4, rowY, panelW - 8, rowH - 2)) {
                manager.searchAuction(item);
                cir.setReturnValue(true);
                return;
            }
            rowY += rowH;
        }

        if (missing.isEmpty()) rowY += rowH;

        int panelH = 31 + visibleRows * rowH + 27;
        int arrangeY = panelY + panelH - 23;
        if (inside(mouseX, mouseY, panelX + 5, arrangeY, panelW - 10, 18)) {
            if (manager.isArranging()) manager.cancelArrange(true);
            else manager.startArrange();
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void scrollPresetMissing(double mouseX, double mouseY, double horizontal, double vertical,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof InventoryScreen) || Client.get() == null) return;

        InventoryPresetManager manager = Client.get().inventoryPresetManager();
        if (manager == null || manager.activePreset().isEmpty()) return;

        int size = manager.missingItems().size();
        if (size <= 8) return;

        HandledScreenAccessor accessor = (HandledScreenAccessor) this;
        int panelX = accessor.getScreenX() + 182;
        int panelY = accessor.getScreenY() + 8;
        int panelH = 31 + 8 * 21 + 27;
        if (!inside(mouseX, mouseY, panelX, panelY, 118, panelH)) return;

        manager.scrollMissing(vertical < 0 ? 1 : -1, size, 8);
        cir.setReturnValue(true);
    }

    @Unique
    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }


    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(CallbackInfo ci) {
        ItemScroller itemScroller = ItemScroller.getInstance();
        if (client == null || !itemScroller.isEnabled()) {
            return;
        }

        boolean isShiftDown = hasShiftDown();

        if (GLFW.glfwGetMouseButton(this.client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS && isShiftDown && focusedSlot != null && focusedSlot.hasStack()) {
            if (System.currentTimeMillis() - time > itemScroller.delay.getValue()) {
                this.onMouseClick(focusedSlot, focusedSlot.id, 0, SlotActionType.QUICK_MOVE);
                time = System.currentTimeMillis();
            }
        }
    }

    @Unique
    private boolean hasShiftDown() {
        return InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), 340) || InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), 344);
    }


    @Unique
    private boolean hasControlDown() {
        return InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), 341) || InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), 345);
    }


        @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (client == null || client.player == null) {
            return;
        }

        LockSlot lockSlot = Client.get().moduleManager().get(LockSlot.class);
        if (client.options.dropKey.matchesKey(input) && focusedSlot != null && focusedSlot.hasStack()
                && lockSlot != null && lockSlot.isDropProtected(focusedSlot.getStack(), toInventorySlot(focusedSlot.id))) {
            cir.setReturnValue(true);
            cir.cancel();
            return;
        }

        ItemScroller itemScroller = ItemScroller.getInstance();
        if (itemScroller == null || !itemScroller.isEnabled()) {
            return;
        }

        if (client.options.dropKey.matchesKey(input) && hasControlDown() && hasShiftDown()) {
            if (focusedSlot != null && focusedSlot.hasStack()) {
                ItemStack hoveredStack = focusedSlot.getStack();
                Item itemToDrop = hoveredStack.getItem();
                for (Slot slot : this.handler.slots) {
                    int inventorySlot = toInventorySlot(slot.id);
                    if (slot.hasStack() && slot.getStack().getItem() == itemToDrop
                            && (lockSlot == null || !lockSlot.isDropProtected(slot.getStack(), inventorySlot))) {
                        this.onMouseClick(slot, slot.id, 1, SlotActionType.THROW);
                    }
                }

                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }

    @Unique
    private int toInventorySlot(int screenSlot) {
        if ((Object) this instanceof InventoryScreen) {
            return InventoryPresetManager.screenToLogicalSlot(screenSlot);
        }
        if ((Object) this instanceof GenericContainerScreen screen) {
            int containerSlots = screen.getScreenHandler().getRows() * 9;
            int relative = screenSlot - containerSlots;
            if (relative >= 0 && relative < 27) return 9 + relative;
            if (relative >= 27 && relative < 36) return relative - 27;
        }
        return -1;
    }
}