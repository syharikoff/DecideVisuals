package ru.white.command.impl;

import ru.white.Client;
import ru.white.command.Command;
import ru.white.module.api.Module;
import ru.white.utils.math.ChatUtils;
import net.minecraft.client.util.InputUtil;


import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BindCommand extends Command {

    public BindCommand() {
        super("bind", ".bind <модуль> <клавиша|none>", "Биндить модуль на клавишу");
    }

    @Override
    
    public void execute(String[] args) {
        if (args.length == 0) {
            showHelp();
            return;
        }

        if (args[0].equalsIgnoreCase("list")) {
            List<Module> bound = Client.get().moduleManager().values().stream()
                    .filter(m -> m.getKey() > 0)
                    .collect(Collectors.toList());
            if (bound.isEmpty()) {
                ChatUtils.addChatMessage("§7Ни один модуль не забинжен");
            } else {
                String names = bound.stream()
                        .map(m -> "§a" + m.getName() + "§7[" + keyName(m.getKey()) + "§7]")
                        .collect(Collectors.joining("§7, "));
                ChatUtils.addChatMessage("§7Бинды §8[" + bound.size() + "§8]§r: " + names);
            }
            return;
        }

        Module module = Client.get().moduleManager().getModule(args[0]);
        if (module == null) {
            ChatUtils.addChatMessage("§cМодуль §f" + args[0] + "§c не найден");
            return;
        }

        // показать текущий бинд
        if (args.length < 2) {
            if (module.getKey() > 0) {
                ChatUtils.addChatMessage("§7" + module.getName() + " забинжен на §a" + keyName(module.getKey()));
            } else {
                ChatUtils.addChatMessage("§7" + module.getName() + " не забинжен");
            }
            return;
        }

        String keyArg = args[1].toLowerCase();
        if (keyArg.equals("none") || keyArg.equals("reset") || keyArg.equals("-")) {
            module.setKey(-1);
            ChatUtils.addChatMessage("§7Бинд §f" + module.getName() + "§7 сброшен");
            return;
        }

        int code = parseKey(keyArg);
        if (code <= 0) {
            ChatUtils.addChatMessage("§cНеизвестная клавиша: §f" + args[1]);
            return;
        }

        module.setKey(code);
        ChatUtils.addChatMessage("§a" + module.getName() + "§r забинжен на §a" + keyName(code));
    }

    
    private static int parseKey(String name) {
        try {
            InputUtil.Key key = InputUtil.fromTranslationKey("key.keyboard." + name);
            return key.getCode();
        } catch (Exception e) {
            return -1;
        }
    }

    
    private static String keyName(int code) {
        try {
            return InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText().getString();
        } catch (Exception e) {
            return "?";
        }
    }

    @Override
    public List<String> getSuggestions(String subPrefix) {
        String[] parts = subPrefix.split(" ", 2);

        // первый аргумент — имя модуля (+ list)
        if (parts.length <= 1) {
            String p = parts[0].toLowerCase();
            List<String> out = new ArrayList<>();
            if ("list".startsWith(p)) out.add("list");
            Client.get().moduleManager().values().stream()
                    .map(Module::getName)
                    .filter(n -> n.toLowerCase().startsWith(p))
                    .forEach(out::add);
            return out;
        }

        // второй аргумент — клавиша
        String moduleName = parts[0];
        String keyPrefix = parts[1].toLowerCase();
        return KEY_SUGGESTIONS.stream()
                .filter(k -> k.startsWith(keyPrefix))
                .map(k -> moduleName + " " + k)
                .collect(Collectors.toList());
    }

    private static final List<String> KEY_SUGGESTIONS = new ArrayList<>();
    static {
        KEY_SUGGESTIONS.add("none");
        for (char c = 'a'; c <= 'z'; c++) KEY_SUGGESTIONS.add(String.valueOf(c));
        for (char c = '0'; c <= '9'; c++) KEY_SUGGESTIONS.add(String.valueOf(c));
        KEY_SUGGESTIONS.add("space");
        KEY_SUGGESTIONS.add("left.shift");
        KEY_SUGGESTIONS.add("right.shift");
        KEY_SUGGESTIONS.add("left.control");
        KEY_SUGGESTIONS.add("left.alt");
        for (int i = 1; i <= 12; i++) KEY_SUGGESTIONS.add("f" + i);
    }

    
    private void showHelp() {
        ChatUtils.addChatMessage("§7.bind §f<модуль> <клавиша>§8 | §7.bind §f<модуль> none§8 | §7.bind list");
    }
}
