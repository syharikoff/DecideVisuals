package ru.white.utils.other;

import ru.white.Client;
import ru.white.module.api.Module;
import lombok.experimental.UtilityClass;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@UtilityClass
public class Instance {

    private final ConcurrentMap<Class<? extends Module>, Module> instanceModules = new ConcurrentHashMap<>();

    public <T extends Module> T get(Class<T> clazz) {
        return clazz.cast(instanceModules.computeIfAbsent(clazz, instance ->Client.get().moduleManager().get(instance)));
    }

    public <T extends Module> T get(String module) {
        return Client.get().moduleManager().get(module);
    }

}
