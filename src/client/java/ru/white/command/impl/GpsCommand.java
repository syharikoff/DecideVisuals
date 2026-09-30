package ru.white.command.impl;

import ru.white.Client;
import ru.white.command.Command;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.ChatUtils;
import ru.white.utils.render.font.Fonts;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.Identifier;
import org.joml.Vector2f;


import java.util.List;

public class GpsCommand extends Command implements IMinecraft {

    // ── Arrow texture (same as Arrows module) ────────────────────────────────
    private static final Identifier ARROW_TEX = Identifier.of("client", "textures/arrow.png");

    // ── Visual constants ─────────────────────────────────────────────────────
    private static final float SIZE   = 20F;   // arrow size in px (same as Arrows)
    private static final float RADIUS = 30F;   // distance from crosshair to arrow TOP

    // ── State ─────────────────────────────────────────────────────────────────
    /** null = GPS off */
    private Vector2f target = null;

    public GpsCommand() {
        super("gps", ".gps <set|off> <x> <z>", "Стрелка на экране ведущая к координатам");
        Client.eventHandler().subscribe(this);
    }

    // ── Command ───────────────────────────────────────────────────────────────

    @Override
    public void execute(String[] args) {
        if (args.length == 0) { showHelp(); return; }

        switch (args[0].toLowerCase()) {
            case "set" -> {
                if (args.length < 3) {
                    ChatUtils.addChatMessage("§7Использование: §f.gps set <x> <z>");
                    return;
                }
                try {
                    float x = Float.parseFloat(args[1]);
                    float z = Float.parseFloat(args[2]);
                    target = new Vector2f(x, z);
                    ChatUtils.addChatMessage("§aGPS §7→ §f" + (int) x + "§7, §f" + (int) z);
                } catch (NumberFormatException ex) {
                    ChatUtils.addChatMessage("§cОшибка: неверные координаты");
                }
            }
            case "off" -> {
                target = null;
                ChatUtils.addChatMessage("§7GPS сброшен");
            }
            default -> showHelp();
        }
    }

    @Override
    public List<String> getSuggestions(String subPrefix) {
        return List.of("set", "off").stream()
                .filter(s -> s.startsWith(subPrefix.toLowerCase()))
                .toList();
    }

    // ── Events ────────────────────────────────────────────────────────────────

    // GPS сохраняется при смене мира (ртп/варп)

    @EventHandler(priority = -500)
    public void onDisplay(EventDisplay e) {
        if (mc.player == null || mc.world == null) return;
        if (target == null) return;
        if (mc.options.hudHidden) return;
        if (!mc.options.getPerspective().equals(Perspective.FIRST_PERSON)) return;

        double dx = (target.x + 0.5) - mc.player.getX();
        double dz = (target.y + 0.5) - mc.player.getZ();
        int dst = (int) Math.sqrt(dx * dx + dz * dz);


        float targetScale = 2F;
        float currentScale = (float) mc.getWindow().getScaleFactor();
        float scaleFix = targetScale / currentScale;

        int screenWidth  = (int) (mc.getWindow().getScaledWidth()  / scaleFix);
        int screenHeight = (int) (mc.getWindow().getScaledHeight() / scaleFix);
        float middleW =screenWidth / 2f;
        float middleH =screenHeight / 2f - 90;

        // ── angle ─────────────────────────────────────────────────────────────
        // same formula as Arrows.getAngle()
        float angleToTarget = (float) -(Math.atan2(dx, dz) * (180.0 / Math.PI));
        float realYaw       = angleToTarget - mc.gameRenderer.getCamera().getYaw();
        float rad           = (float) Math.toRadians(realYaw);

        // ── orbit position (identical to Arrows orbit formula) ────────────────
        // posY is the top edge of the arrow when realYaw = 0 (no rotation)
        float posY   = middleH - RADIUS;
        // offset = distance from the arrow CENTER to screen center, signed negative (arrow above center)
        float offset = posY + SIZE / 2f - middleH;   // = -RADIUS + SIZE/2  (negative)
        float cx     = middleW - offset * (float) Math.sin(rad);
        float cy     = middleH + offset * (float) Math.cos(rad);

        // ── color ─────────────────────────────────────────────────────────────
        int color = ColorUtil.fade(1);

        // ── draw rotating arrow ───────────────────────────────────────────────
        Client.get().render2D().getTexturePipeline().drawGlowTexture(
                ARROW_TEX,
                cx - SIZE / 2f, cy - SIZE / 2f, SIZE, SIZE,
                0f, 0f, 1f, 1f,
                new int[]{ color, color, color, color },
                new float[]{ 0f, 0f, 0f, 0f },
                1f,
                realYaw
        );

        // ── distance label ────────────────────────────────────────────────────
        // placed below the arrow center (same layout as DecideVisuals GPS)
        Fonts.sf_regular.drawCentered(
                dst + "м",
                cx,
                cy + SIZE / 2f + 4f,
                6f,
                ColorUtil.getColor(255, 0.85f)
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showHelp() {
        ChatUtils.addChatMessage("§7.gps §fset §7<x> <z> §8| §7.gps §foff");
    }
}
