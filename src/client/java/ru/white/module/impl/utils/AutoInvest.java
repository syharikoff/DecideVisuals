package ru.white.module.impl.utils;

import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardEntry;
import ru.white.manager.event_impl.EventKey;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BindSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.module.api.settings.impl.StringSetting;
import ru.white.utils.math.ChatUtils;
import ru.white.utils.math.ServerUtil;
import ru.white.utils.notification.NotificationManager;

import java.util.Collection;

@ModuleInfo(
        name = "Auto Invest",
        desc = "Автоматический инвестиции в клан по бинду или авто-порогу",
        category = Category.UTILITIES
)
public class AutoInvest extends Module {

    public final ModeSetting mode = new ModeSetting(this, "Режим", "Бинд", "Авто");
    public final BindSetting investKey = new BindSetting(this, "Клавиша инвеста", -1)
            .setVisible(() -> mode.is("Бинд"));
    public final SliderSetting bindPercent = new SliderSetting(this, "% от баланса (Бинд)", 50.0f, 1.0f, 100.0f, 1.0f)
            .setVisible(() -> mode.is("Бинд"));
    public final StringSetting triggerAmount = new StringSetting(this, "Триггер монет", "10000000")
            .setVisible(() -> mode.is("Авто"));
    public final SliderSetting autoPercent = new SliderSetting(this, "% от баланса (Авто)", 50.0f, 1.0f, 100.0f, 1.0f)
            .setVisible(() -> mode.is("Авто"));


    private int cooldownTicks = 0;

    @EventHandler
    public void onKey(EventKey e) {
        if (mc.player == null || e.getKey() == -1) return;
        if (!mode.is("Бинд")) return;

        if (e.getKey() == investKey.get()) {
            int balance = getBalanceFromScoreboard();
            if (balance <= 0) {
                sendMsg("§cНе удалось определить баланс!");
                return;
            }
            int amount = (int) (balance * (bindPercent.getValue() / 100.0f));
            if (amount <= 0) {
                sendMsg("§cСумма инвеста = 0!");
                return;
            }
            mc.player.networkHandler.sendChatCommand("clan invest " + amount);
            sendMsg("§aИнвестировано §f" + amount + " §aмонет (" + bindPercent.getValue().intValue() + "% от " + balance + ")");
        }
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (mc.player == null || mc.world == null) return;
        if (!mode.is("Авто")) return;

        if (cooldownTicks > 0) {
            --cooldownTicks;
            return;
        }

        int balance = getBalanceFromScoreboard();
        if (balance <= 0) return;

        int trigger;
        try {
            String tv = triggerAmount.getValue().trim();
            trigger = tv.isEmpty() ? 0 : Integer.parseInt(tv);
        } catch (NumberFormatException ex) {
            return;
        }

        if (balance < trigger) return;

        int amount = (int) (balance * (autoPercent.getValue() / 100.0f));
        if (amount <= 0) return;

        mc.player.networkHandler.sendChatCommand("clan invest " + amount);
        sendMsg("§aАвто-инвест: §f" + amount + " §aмонет (" + autoPercent.getValue().intValue() + "% от " + balance + ")");
        cooldownTicks = 100;
    }

    private int getBalanceFromScoreboard() {
        if (mc.world == null || mc.player == null) return -1;
        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) return -1;

        for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(objective)) {
            String line = net.minecraft.scoreboard.Team.decorateName(scoreboard.getScoreHolderTeam(entry.owner()), entry.name()).getString();
            int balance = parseMoneyLine(line);
            if (balance > 0) return balance;
        }
        return -1;
    }

    private int parseMoneyLine(String line) {
        if (line == null) return -1;
        String text = line.replaceAll("§.", "").replaceAll("[\\u00A7].", "").toLowerCase();
        if (!text.contains("монет") && !text.contains("баланс")) return -1;
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return -1;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private void sendMsg(String text) {
        ChatUtils.addChatMessage("§6[AutoInvest] " + text);
        NotificationManager.send(text, NotificationManager.Type.INFO);
    }

    @Override
    public void onDisable() {
        cooldownTicks = 0;
        super.onDisable();
    }
}
