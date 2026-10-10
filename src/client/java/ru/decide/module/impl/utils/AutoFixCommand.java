package ru.decide.module.impl.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.decide.Client;
import ru.decide.module.api.Category;
import ru.decide.module.api.Module;
import ru.decide.module.api.ModuleInfo;
import ru.decide.module.api.settings.impl.BooleanSetting;

import java.util.Locale;
import java.util.Map;

/**
 * Исправляет команды, набранные в русской раскладке.
 * <p>
 * Важная деталь: переводится только имя команды. Аргументы остаются как есть,
 * иначе запрос вида {@code /ah search гриф} превратился бы в
 * {@code /ah search grill} — то есть в поиск по другому слову.
 * <p>
 * Для серверных команд проверяется, что изменилось именно первое слово, а для
 * клиентских — что исправленное имя вообще есть в списке команд: иначе вместо
 * несуществующей команды выполнилась бы другая.
 */
@ModuleInfo(
        name = "Auto Fix Command",
        desc = "Исправляет команды, набранные в русской раскладке",
        category = Category.UTILITIES
)
public class AutoFixCommand extends Module {

    private static final Map<Character, Character> RU_TO_EN = Map.ofEntries(
            Map.entry('й', 'q'),
            Map.entry('ц', 'w'),
            Map.entry('у', 'e'),
            Map.entry('к', 'r'),
            Map.entry('е', 't'),
            Map.entry('н', 'y'),
            Map.entry('г', 'u'),
            Map.entry('ш', 'i'),
            Map.entry('щ', 'o'),
            Map.entry('з', 'p'),
            Map.entry('х', '['),
            Map.entry('ъ', ']'),
            Map.entry('ф', 'a'),
            Map.entry('ы', 's'),
            Map.entry('в', 'd'),
            Map.entry('а', 'f'),
            Map.entry('п', 'g'),
            Map.entry('р', 'h'),
            Map.entry('о', 'j'),
            Map.entry('л', 'k'),
            Map.entry('д', 'l'),
            Map.entry('ж', ';'),
            Map.entry('э', '\''),
            Map.entry('я', 'z'),
            Map.entry('ч', 'x'),
            Map.entry('с', 'c'),
            Map.entry('м', 'v'),
            Map.entry('и', 'b'),
            Map.entry('т', 'n'),
            Map.entry('ь', 'm'),
            Map.entry('б', ','),
            Map.entry('ю', '.'),
            Map.entry('ё', '`')
    );

    private static AutoFixCommand instance;

    public final BooleanSetting serverCommands = new BooleanSetting(this, "Сервер команды", true);
    public final BooleanSetting clientCommands = new BooleanSetting(this, "Клиент команды", true);
    public final BooleanSetting notify = new BooleanSetting(this, "Показывать исправление", true);

    public AutoFixCommand() {
        instance = this;
    }

    public static AutoFixCommand getActive() {
        return instance != null && instance.isEnabled() ? instance : null;
    }

    /** Точка входа для текста из чата: {@code /} — серверная, префикс — клиентская. */
    public static String processChatInput(String message) {
        AutoFixCommand corrector = getActive();
        if (corrector == null || message == null || message.isEmpty()) {
            return message;
        }

        if (!message.startsWith("/")) {
            DotCorrection dotCorrection = correctDotCommand(message);

            if (dotCorrection.type() == DotCorrection.Type.CLIENT) {
                char prefix = prefixChar();
                String corrected = prefix + dotCorrection.command();
                notifyCorrection(message, corrected);
                return corrected;
            } else if (dotCorrection.type() == DotCorrection.Type.SERVER) {
                String corrected = "/" + dotCorrection.command();
                notifyCorrection(message, corrected);
                return corrected;
            }

            return message;
        } else if (!corrector.serverCommands.getValue()) {
            return message;
        }

        String body = message.substring(1);
        String corrected = correctServerCommand(body);

        if (!corrected.equals(body)) {
            notifyCorrection(message, "/" + corrected);
            return "/" + corrected;
        }

        return message;
    }

    /** Точка входа для команд, уходящих напрямую в сеть (бинды, чат-синьки, макросы). */
    public static String processServerCommand(String command) {
        AutoFixCommand corrector = getActive();

        if (corrector == null || command == null || command.isEmpty()
                || !corrector.serverCommands.getValue()) {
            return command;
        }

        String corrected = correctServerCommand(command);

        if (!corrected.equals(command)) {
            notifyCorrection("/" + command, "/" + corrected);
            return corrected;
        }

        return command;
    }

    public static DotCorrection correctDotCommand(String message) {
        AutoFixCommand corrector = getActive();
        if (corrector == null || message == null || message.length() <= 1) {
            return DotCorrection.none(message);
        }

        char prefix = prefixChar();
        String prefixStr = String.valueOf(prefix);
        boolean startsWithPrefix = message.startsWith(prefixStr);
        boolean startsWithDot = message.startsWith(".");
        if (!startsWithPrefix && !startsWithDot) {
            return DotCorrection.none(message);
        }

        String usedPrefix = startsWithPrefix ? prefixStr : ".";
        if (message.length() <= usedPrefix.length()) {
            return DotCorrection.none(message);
        }

        if (isIrcCommand(message)) {
            return DotCorrection.none(message.substring(usedPrefix.length()));
        }

        String body = message.substring(usedPrefix.length());
        if (isKnownClientCommand(firstToken(body))) {
            return DotCorrection.none(body);
        }

        if (corrector.clientCommands.getValue()) {
            String clientCommand = correctClientCommand(body);
            if (!clientCommand.equals(body)) {
                return DotCorrection.client(clientCommand);
            }
        }

        if (corrector.serverCommands.getValue()) {
            String serverCommand = correctServerCommand(body);
            if (!serverCommand.equals(body)) {
                return DotCorrection.server(serverCommand);
            }
        }

        return DotCorrection.none(body);
    }

    public static String correctServerCommand(String command) {
        if (command == null || command.isEmpty()) {
            return command;
        }

        if (isAhSearchCommand(command)) {
            return fixAhSearchCommand(command);
        }

        String converted = convertRuToEn(command);
        if (converted.equals(command)) {
            return command;
        }

        String originalLabel = firstToken(command);
        String convertedLabel = firstToken(converted);
        return convertedLabel.equals(originalLabel) ? command : converted;
    }

    public static String correctClientCommand(String command) {
        if (command == null || command.isEmpty()) {
            return command;
        }

        String converted = convertRuToEn(command);
        if (converted.equals(command)) {
            return command;
        }

        String label = firstToken(converted).toLowerCase(Locale.ROOT);
        return !isKnownClientCommand(label) ? command : converted;
    }

    public static String convertRuToEn(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder result = new StringBuilder(text.length());

        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            Character mapped = RU_TO_EN.get(Character.toLowerCase(current));

            if (mapped == null) {
                result.append(current);
            } else if (Character.isUpperCase(current)) {
                result.append(Character.toUpperCase(mapped));
            } else {
                result.append(mapped);
            }
        }

        return result.toString();
    }

    public static void notifyCorrection(String from, String to) {
        AutoFixCommand corrector = getActive();
        if (corrector == null || !corrector.notify.getValue()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.inGameHud == null) {
            return;
        }

        Text message = Text.literal("AutoFixCommand: ")
                .formatted(Formatting.RED)
                .append(Text.literal(from).formatted(Formatting.WHITE))
                .append(Text.literal(" -> ").formatted(Formatting.YELLOW))
                .append(Text.literal(to).formatted(Formatting.GREEN));

        client.player.sendMessage(message, false);
    }

    private static char prefixChar() {
        try {
            return Client.get().commandManager().getPrefix();
        } catch (Throwable ignored) {
            return '.';
        }
    }

    private static boolean isKnownClientCommand(String label) {
        if (label == null || label.isBlank()) {
            return false;
        }

        try {
            String clean = label.toLowerCase(Locale.ROOT);

            for (String known : Client.get().commandManager().getCommandNames()) {
                if (clean.equals(known.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            return false;
        }

        return false;
    }

    /** {@code фр ыуфкср ник} -> {@code ah search ник}: иначе поиск уходит по мусорному запросу. */
    private static boolean isAhSearchCommand(String command) {
        String body = command.startsWith("/") ? command.substring(1) : command;
        String[] tokens = body.stripLeading().split("\\s+", 3);

        if (tokens.length < 2) {
            return false;
        }

        String first = tokens[0].toLowerCase(Locale.ROOT);
        String second = tokens[1].toLowerCase(Locale.ROOT);
        boolean firstMatches = first.equals("фр") || first.equals("ah");
        boolean secondMatches = second.equals("ыуфкср") || second.equals("search");
        return firstMatches && secondMatches;
    }

    private static String fixAhSearchCommand(String command) {
        boolean hasSlash = command.startsWith("/");
        String body = hasSlash ? command.substring(1) : command;
        String[] tokens = body.stripLeading().split("\\s+", 3);
        String prefix = hasSlash ? "/ah search" : "ah search";
        return tokens.length >= 3 ? prefix + " " + tokens[2] : prefix;
    }

    private static String firstToken(String text) {
        String trimmed = text.stripLeading();
        int space = trimmed.indexOf(' ');
        return space == -1 ? trimmed : trimmed.substring(0, space);
    }

    private static boolean isIrcCommand(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.equals(".irc") || lower.startsWith(".irc ");
    }

    public record DotCorrection(Type type, String command) {

        public static DotCorrection none(String command) {
            return new DotCorrection(Type.NONE, command);
        }

        public static DotCorrection client(String command) {
            return new DotCorrection(Type.CLIENT, command);
        }

        public static DotCorrection server(String command) {
            return new DotCorrection(Type.SERVER, command);
        }

        public enum Type {
            NONE,
            CLIENT,
            SERVER
        }
    }
}