package ru.white.command.impl;

import ru.white.command.Command;
import ru.white.manager.neuro.NeuroManager;
import ru.white.manager.neuro.NeuroModel;
import ru.white.utils.math.ChatUtils;

import java.util.List;
import java.util.stream.Collectors;

public class NeuroCommand extends Command {

    private static final List<String> SUBCOMMANDS = List.of("create", "select", "train", "stop", "list", "delete", "info");

    public NeuroCommand() {
        super("neuro", ".neuro <create|select|train|stop|list|delete|info> [имя]", "Нейро-ротация: модели и тренировка");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            help();
            return;
        }

        NeuroManager nm = NeuroManager.get();

        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (args.length < 2) { ChatUtils.addChatMessage("§7Использование: §f.neuro create <имя>"); return; }
                if (nm.create(args[1])) ChatUtils.addChatMessage("§a[Neuro] §7Модель §f" + args[1] + "§7 создана и выбрана");
                else ChatUtils.addChatMessage("§cМодель §f" + args[1] + "§c уже существует");
            }
            case "select" -> {
                if (args.length < 2) { ChatUtils.addChatMessage("§7Использование: §f.neuro select <имя>"); return; }
                if (nm.select(args[1])) ChatUtils.addChatMessage("§a[Neuro] §7Выбрана модель §f" + args[1]);
                else ChatUtils.addChatMessage("§cМодель §f" + args[1] + "§c не найдена");
            }
            case "train" -> nm.startTrain();
            case "stop" -> nm.stopTrain();
            case "delete" -> {
                if (args.length < 2) { ChatUtils.addChatMessage("§7Использование: §f.neuro delete <имя>"); return; }
                if (nm.delete(args[1])) ChatUtils.addChatMessage("§c[Neuro] §7Модель §f" + args[1] + "§7 удалена");
                else ChatUtils.addChatMessage("§cМодель §f" + args[1] + "§c не найдена");
            }
            case "list" -> {
                if (nm.all().isEmpty()) { ChatUtils.addChatMessage("§7Моделей нет. §f.neuro create <имя>"); return; }
                String names = nm.all().stream()
                        .map(m -> (m == nm.getCurrent() ? "§a" : "§f") + m.name + "§7(" + m.samples + ")")
                        .collect(Collectors.joining("§7, "));
                ChatUtils.addChatMessage("§7Модели: " + names);
            }
            case "info" -> {
                NeuroModel c = nm.getCurrent();
                if (c == null) { ChatUtils.addChatMessage("§7Модель не выбрана"); return; }
                ChatUtils.addChatMessage(String.format("§a[Neuro] §f%s§7 — образцов: §f%d§7, ошибка: §f%s",
                        c.name, c.samples, c.loss < 0 ? "—" : String.format("%.4f", c.loss)));
            }
            default -> help();
        }
    }

    @Override
    public List<String> getSuggestions(String subPrefix) {
        return SUBCOMMANDS.stream()
                .filter(s -> s.startsWith(subPrefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    private void help() {
        ChatUtils.addChatMessage("§7.neuro §fcreate §7<имя> §8| §fselect §7<имя> §8| §ftrain §8| §fstop §8| §flist §8| §finfo §8| §fdelete §7<имя>");
        ChatUtils.addChatMessage("§8Тренировка: §7.neuro create my §8→ §7.neuro train §8→ води прицелом за дамми §8→ §7.neuro stop");
    }
}
