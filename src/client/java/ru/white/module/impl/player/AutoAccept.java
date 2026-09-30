package ru.white.module.impl.player;

import ru.white.Client;
import ru.white.manager.event_impl.EventPacket;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.MultiBooleanSetting;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.List;

@ModuleInfo(name = "Auto Accept", category = Category.UTILITIES, desc = "Автоматически принимает запросы на телепортацию и в клан")
public class AutoAccept extends Module {

    public final MultiBooleanSetting type = new MultiBooleanSetting(this,"Принимать", new BooleanSetting("Запрос на ТП", true), new BooleanSetting("Запрос в клан", true));
    public final ModeSetting mode = new ModeSetting(this,"Принимать ТП от", "Друзей", "Всех").setVisible(() -> type.get("Запрос на ТП").getValue());
    public final BooleanSetting onlyFriends = new BooleanSetting("Принимать запрос в клан только от друзей", true).setVisible(() -> type.get("Запрос в клан").getValue());

    private boolean canAccept;
    private boolean waitingForClan = false;
    private long clanInviteTime = 0;

    public AutoAccept() {

    }

    private final String[] teleportMessages = new String[]{
            "has requested teleport",
            "просит телепортироваться",
            "хочет телепортироваться к вам",
            "просит к вам телепортироваться"
    };

    private final String[] clanMessage = new String[]{
            "приглашает вас в клан",
            "приглашает Вас в клан",
            "invited you to clan"
    };

    @EventHandler
    public void onPacket(EventPacket packetEvent) {
        if(mc.player != null && mc.world != null) {
            if (packetEvent.getPacket() instanceof GameMessageS2CPacket m) {
                Text content = m.content();
                String message = content.getString();

                if (waitingForClan) {
                    if (System.currentTimeMillis() - clanInviteTime > 5000) {
                        waitingForClan = false;
                    } else {
                        String cmd = findClickCommand(content, "Вступить");
                        if (cmd != null) {
                            runCommand(cmd);
                            waitingForClan = false;
                            return;
                        }
                    }
                }

                if (type.getValue("Запрос на ТП") && isTeleportMessage(message)) {
                    if (mode.is("Всех")) {
                        canAccept = true;
                    } else {
                        String lowerMessage = message.toLowerCase();
                        for (String friendName : Client.get().friendManager().getFriends()) {
                            if (lowerMessage.contains(friendName.toLowerCase())) {
                                canAccept = true;
                                break;
                            }
                        }
                    }
                }

                if (type.getValue("Запрос в клан") && isClanMessage(message)) {
                    String playerName = extractName(message, "приглашает");

                    if (playerName != null && !checkFriend(playerName)) {
                        return;
                    }

                    String cmd = findClickCommand(content, "Вступить");
                    if (cmd != null) {
                        runCommand(cmd);
                    } else {
                        waitingForClan = true;
                        clanInviteTime = System.currentTimeMillis();
                    }
                }

            }
        }
    }

    @EventHandler
    public void onTick(EventUpdate e) {
        if (canAccept && mc.player != null) {
            mc.player.networkHandler.sendChatCommand("tpaccept");
            canAccept = false;
        }
    }

    private String findClickCommand(Text text, String buttonText) {
        if (text == null)
            return null;

        if (text.getStyle() != null && text.getStyle().getClickEvent() != null) {
            ClickEvent ce = text.getStyle().getClickEvent();

            if (text.getString().contains(buttonText)) {
                if (ce instanceof ClickEvent.RunCommand runCommand) {
                    return runCommand.command();
                } else if (ce instanceof ClickEvent.SuggestCommand suggestCommand) {
                    return suggestCommand.command();
                }
            }
        }

        List<Text> siblings = text.getSiblings();
        if (siblings != null) {
            for (Text sibling : siblings) {
                String cmd = findClickCommand(sibling, buttonText);
                if (cmd != null)
                    return cmd;
            }
        }
        return null;
    }

    private void runCommand(String cmd) {
        if (mc.player == null || mc.player.networkHandler == null)
            return;
        if (cmd.startsWith("/")) {
            mc.player.networkHandler.sendChatCommand(cmd.substring(1));
        } else {
            mc.player.networkHandler.sendChatCommand(cmd);
        }
    }

    private boolean checkFriend(String playerName) {
        if (!onlyFriends.getValue())
            return true;
        return Client.get().friendManager().isFriend(playerName);
    }

    private String extractName(String message, String keyword) {
        String clean = message.replaceAll("§.", "");
        int index = clean.indexOf(keyword);
        if (index <= 0)
            return null;

        String before = clean.substring(0, index).trim();
        int lastSpace = before.lastIndexOf(' ');
        if (lastSpace >= 0) {
            return before.substring(lastSpace + 1).trim();
        }
        return before.trim();
    }

    private boolean isTeleportMessage(String message) {
        String lowerCaseMessage = message.toLowerCase();
        return Arrays.stream(this.teleportMessages).anyMatch(trigger -> lowerCaseMessage.contains(trigger.toLowerCase()));
    }

    private boolean isClanMessage(String message) {
        String lowerCaseMessage = message.toLowerCase();
        return Arrays.stream(this.clanMessage).anyMatch(trigger -> lowerCaseMessage.contains(trigger.toLowerCase()));
    }
}