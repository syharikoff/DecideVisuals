package ru.white.command.impl;

import ru.white.Client;
import ru.white.command.Command;
import ru.white.utils.math.ChatUtils;


import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class CfgCommand extends Command {
    private static final List<String> SUBCOMMANDS = List.of("save", "load", "list", "dir");

    public CfgCommand() {
        super("cfg", ".cfg <save|load|list|dir> [name]", "Конфиги");
    }

    @Override
    
    public void execute(String[] args) {
        if (args.length == 0) {
            showHelp();
            return;
        }

        switch (args[0].toLowerCase()) {
            case "save" -> {
                if (args.length < 2) { ChatUtils.addChatMessage("Использование: .cfg save <название>"); return; }
                Client.get().configManager().save(args[1]);
                ChatUtils.addChatMessage("Конфиг §a" + args[1] + "§r сохранён");
            }
            case "load" -> {
                if (args.length < 2) { ChatUtils.addChatMessage("Использование: .cfg load <название>"); return; }
                boolean ok = Client.get().configManager().load(args[1]);
                if (ok) ChatUtils.addChatMessage("Конфиг §a" + args[1] + "§r загружен");
                else ChatUtils.addChatMessage("§cКонфиг §r" + args[1] + "§c не найден");
            }
            case "list" -> {
                List<String> configs = Client.get().configManager().listConfigs();
                if (configs.isEmpty()) {
                    ChatUtils.addChatMessage("§7Нет сохранённых конфигов");
                } else {
                    ChatUtils.addChatMessage("§7Конфиги §8[" + configs.size() + "§8]§r: " + String.join("§7, §r", configs));
                }
            }
            case "dir" -> {
                String path = Client.get().configManager().getConfigDir().toAbsolutePath().toString();
                try {
                    new ProcessBuilder("explorer.exe", path).start();
                } catch (IOException e) {
                    ChatUtils.addChatMessage("§cНе удалось открыть папку: " + path);
                }
            }
            default -> showHelp();
        }
    }

    @Override
    public List<String> getSuggestions(String subPrefix) {
        return SUBCOMMANDS.stream()
                .filter(s -> s.startsWith(subPrefix.toLowerCase()))
                .collect(Collectors.toList());
    }
    
    private void showHelp() {
        ChatUtils.addChatMessage("§7.cfg save/load §f<name> §8| §7.cfg list §8| §7.cfg dir");
    }
}
