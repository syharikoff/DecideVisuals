package ru.white.command.impl;

import ru.white.Client;
import ru.white.command.Command;
import ru.white.utils.math.ChatUtils;


import java.util.List;

public class PrefixCommand extends Command {

    public PrefixCommand() {
        super("prefix", ".prefix <символ>", "Изменить префикс команд");
    }

    @Override
    
    public void execute(String[] args) {
        if (args.length == 0 || args[0].isEmpty()) {
            char cur = Client.get().commandManager().getPrefix();
            ChatUtils.addChatMessage("§7Текущий префикс: §a" + cur);
            ChatUtils.addChatMessage("§7Использование: " + cur + "prefix <символ>");
            return;
        }
        char newPrefix = args[0].charAt(0);
        Client.get().commandManager().setPrefix(newPrefix);
        ChatUtils.addChatMessage("§7Префикс изменён на: §a" + newPrefix);
    }

    @Override
    public List<String> getSuggestions(String subPrefix) {
        return List.of();
    }
}
