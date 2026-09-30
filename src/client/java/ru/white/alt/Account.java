package ru.white.alt;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Один сохранённый аккаунт. Пока поддерживаются только оффлайн (cracked) аккаунты —
 * для них UUID детерминированно выводится из ника, как это делает Minecraft.
 */
public class Account {

    public enum Type { OFFLINE }

    public String name;
    public Type type;
    public long created; // время добавления (epoch ms)
    public boolean favorite;

    public Account(String name, Type type, long created) {
        this.name = name;
        this.type = type;
        this.created = created;
    }

    public Account(String name) {
        this(name, Type.OFFLINE, System.currentTimeMillis());
    }

    /** Оффлайн-UUID, идентичный тому, что генерирует ванильный сервер для cracked-игроков. */
    public UUID offlineUuid() {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    /** URL головы скина (face + hat) для аватарки. */
    public String headUrl() {
        return "https://minotar.net/helm/" + name + "/64.png";
    }
}
