package ru.white.command;

import ru.white.command.impl.BindCommand;
import ru.white.command.impl.CfgCommand;
import ru.white.command.impl.FriendCommand;
import ru.white.command.impl.GpsCommand;
import ru.white.command.impl.NeuroCommand;
import ru.white.command.impl.PrefixCommand;
import ru.white.command.impl.SpecCommand;
import ru.white.command.impl.WayCommand;
import ru.white.utils.math.ChatUtils;


import java.util.*;
import java.util.stream.Collectors;

public class CommandManager {
    private char prefix = '.';
    private final Map<String, Command> commands = new LinkedHashMap<>();
    
    public void init() {
        register(new CfgCommand());
        register(new FriendCommand());
        register(new PrefixCommand());
        register(new BindCommand());
        register(new GpsCommand());
        register(new WayCommand());
        register(new SpecCommand());
    }

    public void register(Command command) {
        commands.put(command.getName().toLowerCase(), command);
    }

    public char getPrefix() { return prefix; }
    public void setPrefix(char prefix) { this.prefix = prefix; }

    public boolean handleMessage(String message) {
        if (message.isEmpty() || message.charAt(0) != prefix) return false;

        String[] parts = message.substring(1).trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            showAll();
            return true;
        }

        String commandName = parts[0].toLowerCase();
        Command command = commands.get(commandName);

        if (command == null) {
            ChatUtils.addChatMessage("§cНеизвестная команда: §r" + commandName);
            showAll();
            return true;
        }

        command.execute(Arrays.copyOfRange(parts, 1, parts.length));
        return true;
    }

    public List<String> getSuggestions(String input) {
        if (input.isEmpty() || input.charAt(0) != prefix) return Collections.emptyList();

        String without = input.substring(1);

        if (!without.contains(" ")) {
            return commands.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(without.toLowerCase()))
                    .map(e -> prefix + e.getKey())
                    .collect(Collectors.toList());
        }

        String[] parts = without.split(" ", 2);
        String cmdName = parts[0].toLowerCase();
        String subPrefix = parts.length > 1 ? parts[1] : "";

        Command cmd = commands.get(cmdName);
        if (cmd == null) return Collections.emptyList();

        return cmd.getSuggestions(subPrefix).stream()
                .map(s -> prefix + cmdName + " " + s)
                .collect(Collectors.toList());
    }

    private void showAll() {
        String list = commands.keySet().stream()
                .map(n -> "§a" + prefix + n)
                .collect(Collectors.joining("§7, "));
        ChatUtils.addChatMessage("§7Команды: " + list);
    }
}
