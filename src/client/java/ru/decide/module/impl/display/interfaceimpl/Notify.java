package ru.decide.module.impl.display.interfaceimpl;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2fStack;
import ru.decide.Client;
import ru.decide.manager.event_impl.EventDisplay;
import ru.decide.module.api.Category;
import ru.decide.module.api.Module;
import ru.decide.module.api.settings.impl.BooleanSetting;
import ru.decide.module.api.settings.impl.DragSetting;
import ru.decide.module.impl.display.InterFace;
import ru.decide.theme.ThemeColor;
import ru.decide.utils.animation.Animation;
import ru.decide.utils.animation.Easings;
import ru.decide.utils.annotation.IMinecraft;
import ru.decide.utils.colors.ColorFormatting;
import ru.decide.utils.colors.ColorUtil;
import ru.decide.utils.notification.NotificationManager;
import ru.decide.utils.render.ItemRender;
import ru.decide.utils.render.RenderUtil;
import ru.decide.utils.render.font.Fonts;

import java.util.*;

public class Notify implements element {

    private final Animation notifyGhostAnim = new Animation();
    private final Set<String> notifiedEffectExpiry = new HashSet<>();
    private final Map<String, Boolean> prevModuleStates = new HashMap<>();
    private boolean moduleStatesInitialized = false;
    private final Set<EquipmentSlot> armorNotified = new HashSet<>();

    // --- Переменные для логики масштабирования ---
    private static float S = 1.0F;

    private static float FONT_GHOST = 5.5F * S;
    private static float FONT_MAIN = 6.5F * S;

    private static float RADIUS = 5F * S;
    private static float ITEM_H = 16F * S;
    private static float GLOW_H = 15.5F * S;
    private static float STEP_Y = 22F * S;

    private static float GHOST_W = 80F * S;
    private static float GHOST_H = 14F * S;

    // Базовые отступы ширины (высчитаны из оригинального кода: 4+13+4+3=24 и 4+13+4+3+4-16=12)
    private static float STACK_BASE_W = 24F * S;
    private static float ITEM_BASE_W = 12F * S;

    public void onTick(BooleanSetting notifModules, BooleanSetting notifArmor, BooleanSetting notifEffects) {
        if (mc.player == null || mc.world == null) return;

        Collection<Module> allMods = Client.get().moduleManager().values();
        if (!moduleStatesInitialized) {
            for (Module m : allMods) prevModuleStates.put(m.getName(), m.isEnabled());
            moduleStatesInitialized = true;
        } else if (notifModules.getValue()) {
            for (Module m : allMods) {
                boolean prev = prevModuleStates.getOrDefault(m.getName(), false);
                boolean curr = m.isEnabled();
                if (prev != curr) {
                    String g = switch (m.getCategory()) {
                        case VISUALS    -> "u";
                        case HUD        -> "H";
                        case UTILITIES  -> "L";
                        case COSMETICS  -> "C";
                        case CONFIGS    -> "P";
                        case MARKERS    -> "S";
                    };
                    NotificationManager.send(
                            "Функция " + m.getBigName() + (curr ? " активирована" : " деактивирована"),
                            NotificationManager.Type.MODULE, m.getBigName());
                }
                prevModuleStates.put(m.getName(), curr);
            }
        } else {
            for (Module m : allMods) prevModuleStates.put(m.getName(), m.isEnabled());
        }

        if (notifArmor.getValue()) {
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (EquipmentSlot slot : slots) {
                ItemStack stack = mc.player.getEquippedStack(slot);
                if (stack.isEmpty() || stack.getMaxDamage() == 0) {
                    armorNotified.remove(slot);
                    continue;
                }
                float dur = 1f - (float) stack.getDamage() / stack.getMaxDamage();
                if (dur < 0.1f && !armorNotified.contains(slot)) {
                    armorNotified.add(slot);
                    String slotName = switch (slot) {
                        case HEAD  -> "Шлем";
                        case CHEST -> "Нагрудник";
                        case LEGS  -> "Поножи";
                        case FEET  -> "Ботинки";
                        default    -> slot.getName();
                    };
                    NotificationManager.send(slotName + " почти сломан!", NotificationManager.Type.WARNING, 5000);
                } else if (dur >= 0.1f) {
                    armorNotified.remove(slot);
                }
            }
        }

        if (notifEffects.getValue()) {
            for (StatusEffectInstance effect : mc.player.getStatusEffects()) {
                String name = effect.getEffectType().value().getName().getString();
                int ticks = effect.getDuration();
                if (ticks <= 0) continue;
                if (ticks > 200) {
                    notifiedEffectExpiry.remove(name);
                } else if (!notifiedEffectExpiry.contains(name)) {
                    notifiedEffectExpiry.add(name);
                    Identifier tex = getEffectTexture(effect.getEffectType());
                    NotificationManager.send(name + " заканчивается!", NotificationManager.Type.EFFECT, tex, 4000);
                }
            }
        }
    }

    public void onRender(DragSetting drag, InterFace interFace, EventDisplay eventDisplay) {
        // Обновляем значения скейла каждый кадр
        S = InterFace.getInstance().sizeHud.getValue();
        FONT_GHOST = 5.5F * S;
        FONT_MAIN = 6.5F * S;
        RADIUS = 5F * S;
        ITEM_H = 16F * S;
        GLOW_H = 15.5F * S;
        STEP_Y = 22F * S;
        GHOST_W = 80F * S;
        GHOST_H = 14F * S;
        STACK_BASE_W = 24F * S;
        ITEM_BASE_W = 12F * S;

        float targetScale = 2F;
        float currentScale = (float) mc.getWindow().getScaleFactor();
        float scaleFix = targetScale / currentScale;

        int screenWidth = (int) (mc.getWindow().getScaledWidth() / scaleFix);
        float alpha = getAlpha();

        synchronized (NotificationManager.entries()) {
            List<NotificationManager.Entry> notifs = NotificationManager.entries();
            notifs.removeIf(e -> e.removing && e.anim.get() <= 0.01f);

            for (NotificationManager.Entry e : notifs) {
                e.anim.update();
                if (e.isExpired()) e.removing = true;
                e.anim.run(e.removing ? 0f : 1f, 0.15f, Easings.SINE_OUT);
            }

            float stackW = GHOST_W;
            float stackH = 0;
            for (NotificationManager.Entry e : notifs) {
                float a = e.anim.get();
                if (a <= 0.01f) continue;
                float tw = Fonts.sf_regular.getWidth(e.text, FONT_GHOST);
                stackW = Math.max(stackW, STACK_BASE_W + tw);
                stackH += (ITEM_H + 2 * S) * a;
            }
            if (stackH <= 0) stackH = ITEM_H;

            drag.size.set(stackW, stackH);
            drag.targetPosition.x = screenWidth / 2f - stackW / 2f;
            drag.position.x = screenWidth / 2f - stackW / 2f;

            float ny = drag.position.y;

            boolean showGhost = mc.currentScreen instanceof ChatScreen
                    && notifs.stream().noneMatch(e -> e.anim.get() > 0.01f);

            drag.active = showGhost || notifs.stream().anyMatch(e -> e.anim.get() > 0.01f);

            notifyGhostAnim.update();
            notifyGhostAnim.run(showGhost ? 1 : 0, 0.2, Easings.BACK_OUT, true);
            float ga = MathHelper.clamp(notifyGhostAnim.get(), 0F, 1F);

            if (ga > 0.01F) {
                float gw = GHOST_W * (0.6F + 0.4F * notifyGhostAnim.get());
                float gh = GHOST_H * (0.6F + 0.4F * notifyGhostAnim.get());
                float gx = screenWidth / 2f - gw / 2f;
                float gy = ny + (GHOST_H - gh) / 2f;

                RenderUtil.Render2D.glow(gx, gy, gw, gh, ColorUtil.getColor(0, 0.1f * ga), RADIUS, 8, 1);
                RenderUtil.Blur.blur(gx, gy, gw, gh, 1f, RADIUS,
                        ColorUtil.replAlpha(ColorUtil.background(), Math.min(ga * InterFace.getInstance().alphaHUD.getValue() + 0.35F, 1.0F)));
                Fonts.sf_regular.draw("Уведомления",
                        screenWidth / 2f - Fonts.sf_regular.getWidth("Уведомления", FONT_GHOST) / 2f,
                        gy + (gh - FONT_GHOST) / 2f, FONT_GHOST, ColorUtil.getColor(175, 0.6F * ga));
            }

            for (NotificationManager.Entry e : notifs) {
                float a = e.anim.get();
                if (a <= 0.01f) continue;

                float tw = Fonts.sf_regular.getWidth(e.text, FONT_MAIN);
                float iconW = 10F * S;
                float padX = 5F * S;
                float nw = iconW + padX + tw + padX;

                float nx = screenWidth / 2f - nw / 2f;
                float actualY = ny;

                RenderUtil.Render2D.glow(nx, actualY, nw, ITEM_H, ColorUtil.getColor(0, 0.1f * a), RADIUS, 8, 1);
                RenderUtil.Blur.blur(nx, actualY, nw, ITEM_H, 1f, RADIUS,
                        ColorUtil.replAlpha(ColorUtil.background(), Math.min(a * InterFace.getInstance().alphaHUD.getValue() + 0.35F, 1.0F)));

                drawIcon(eventDisplay, e, nx + 2F * S, actualY, a, scaleFix);

                Fonts.sf_regular.draw(e.text, nx + iconW + padX, actualY + (ITEM_H - FONT_MAIN) / 2, FONT_MAIN,
                        ColorUtil.replAlpha(ColorUtil.getColor(255), a * 0.95f));

                ny += STEP_Y * a;
            }
        }
    }

    private void drawIcon(EventDisplay eventDisplay, NotificationManager.Entry e,
                          float nx, float actualY, float a, float scaleFix) {
        if (e.itemStack != null) {
            Matrix3x2fStack matrix = eventDisplay.getDrawContext().getMatrices();
            matrix.pushMatrix();
            matrix.translate((nx + 8.5F * S) * scaleFix, (actualY + 7 * S) * scaleFix);
            matrix.scale(0.6F * S, 0.6F * S);
            ItemRender.drawItemWithContext(eventDisplay.getDrawContext(), e.itemStack, -8, -8, a, a);
            matrix.popMatrix();
        } else if (e.effectTexture != null) {
            Matrix3x2fStack matrix = eventDisplay.getDrawContext().getMatrices();
            matrix.pushMatrix();
            matrix.translate((nx + 5 * S) * scaleFix, (actualY + 2.25F * S) * scaleFix);
            eventDisplay.getDrawContext().drawGuiTexture(
                    RenderPipelines.GUI_TEXTURED, e.effectTexture,
                    0, 0, (int) (8 * S * scaleFix), (int) (8 * S * scaleFix),
                    ColorUtil.getColor(255, a));
            matrix.popMatrix();
        } else {
            float iconFont = 6F * S;
            if (e.text.contains("почти")) {
                Fonts.icon.draw("r", nx + 6 * S, actualY + 3.8F * S, iconFont, ColorUtil.getColorRectMain(a));
            }
            if (e.text.contains("активирована") && !e.text.contains("деактивирована")) {
                Fonts.icon.draw("2", nx + 6 * S, actualY + 3.6F * S, iconFont, ColorUtil.getColorRectMain(a));
            } else if (e.text.contains("деактивирована")) {
                Fonts.icon.draw("1", nx + 6 * S, actualY + 3.6F * S, iconFont, ColorUtil.getColorRectMain(a));
            }
        }
    }

    private static String formatText(NotificationManager.Entry e, float a, float alpha) {
        if (e.iconGlyph == null || e.iconGlyph.isEmpty() || !e.text.contains(e.iconGlyph)) {
            return e.text;
        }
        return e.text.replace(e.iconGlyph, ColorFormatting.getColor(ThemeColor.getHudColor(a * alpha))
                + e.iconGlyph + ColorFormatting.reset());
    }

    private static float getAlpha() {
        return ThemeColor.getOpacity() < 0.98f ? ThemeColor.getOpacity() : 0.98F;
    }

    private static Identifier getEffectTexture(RegistryEntry<StatusEffect> effect) {
        return effect.getKey()
                .map(RegistryKey::getValue)
                .map(id -> id.withPrefixedPath("mob_effect/"))
                .orElse(Identifier.ofVanilla("mob_effect/speed"));
    }

    @Override
    public void onRender(DragSetting drag, InterFace interFace) {
    }
}
