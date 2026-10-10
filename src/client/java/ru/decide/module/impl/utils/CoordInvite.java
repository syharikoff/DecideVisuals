package ru.decide.module.impl.utils;

import net.minecraft.util.math.BlockPos;
import ru.decide.manager.event_impl.EventKey;
import ru.decide.manager.events.orbit.EventHandler;
import ru.decide.module.api.Category;
import ru.decide.module.api.Module;
import ru.decide.module.api.ModuleInfo;
import ru.decide.module.api.settings.impl.BindSetting;
import ru.decide.module.api.settings.impl.StringSetting;

/**
 * По нажатию бинда отправляет игроку личку с текущими координатами:
 * {@code /msg <ник> X Y Z}.
 */
@ModuleInfo(
        name = "Coord Invite",
        desc = "Отправляет координаты игроку по бинду",
        category = Category.UTILITIES
)
public class CoordInvite extends Module {

    private static final long INVITE_COOLDOWN_MS = 400L;

    public BindSetting inviteBind = new BindSetting(this, "Бинд приглашения", -1);
    public StringSetting playerName = new StringSetting(this, "Ник игрока", "Aka_xxx");

    private long lastInviteMs;

    @Override
    protected void onEnable() {
        lastInviteMs = 0L;
    }

    @EventHandler
    public void onKey(EventKey event) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (mc.player.networkHandler == null) return;

        int key = inviteBind.get();
        if (key <= 0 || event.getKey() != key) return;

        long now = System.currentTimeMillis();
        if (now - lastInviteMs < INVITE_COOLDOWN_MS) return;
        lastInviteMs = now;

        sendInvite();
    }

    private void sendInvite() {
        String nick = playerName.getValue() == null ? "" : playerName.getValue().trim();
        if (nick.isEmpty()) return;

        BlockPos pos = mc.player.getBlockPos();
        // именно sendChatCommand: sendChatMessage отправит "/msg ..." как обычное сообщение в чат
        mc.player.networkHandler.sendChatCommand(
                "msg " + nick + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }
}