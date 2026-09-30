package ru.white.command.impl;

import ru.white.Client;
import ru.white.command.Command;
import ru.white.utils.math.ChatUtils;


import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FriendCommand extends Command {
    private static final List<String> SUBCOMMANDS = List.of("add", "remove", "list");

    public FriendCommand() {
        super("friend", ".friend <add|remove|list> [name]", "Список друзей");
    }

    @Override
    
    public void execute(String[] args) {
        if (args.length == 0) {
            showHelp();
            return;
        }

        switch (args[0].toLowerCase()) {
            case "add" -> {
                if (args.length < 2) { ChatUtils.addChatMessage("Использование: .friend add <ник>"); return; }
                String name = args[1];
                boolean ok = Client.get().friendManager().add(name);
                if (ok) ChatUtils.addChatMessage("§a" + name + "§r добавлен в друзья");
                else ChatUtils.addChatMessage("§7" + name + " уже в друзьях");
            }
            case "remove" -> {
                if (args.length < 2) { ChatUtils.addChatMessage("Использование: .friend remove <ник>"); return; }
                String name = args[1];
                boolean ok = Client.get().friendManager().remove(name);
                if (ok) ChatUtils.addChatMessage("§c" + name + "§r удалён из друзей");
                else ChatUtils.addChatMessage("§7" + name + " не в друзьях");
            }
            case "list" -> {
                Set<String> list = Client.get().friendManager().getFriends();
                if (list.isEmpty()) {
                    ChatUtils.addChatMessage("§7Список друзей пуст");
                } else {
                    String names = list.stream().collect(Collectors.joining("§7, §a"));
                    ChatUtils.addChatMessage("§7Друзья §8[" + list.size() + "§8]§r: §a" + names);
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
        ChatUtils.addChatMessage("§7.friend add/remove §f<ник> §8| §7.friend list");
    }
}
