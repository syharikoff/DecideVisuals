package ru.white.utils.render;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

/**
 * Определение, активен ли сейчас шейдерпак Iris/Oculus.
 *
 * Iris не является зависимостью клиента, поэтому проверка делается через рефлексию:
 * если мод не установлен — методы просто возвращают false и ничего не стоят.
 *
 * Нужно, чтобы пост-эффекты, которые читают/пишут ванильный фреймбуфер во время
 * отрисовки (например {@link GlassHandsRenderer}), отключались под шейдерами — Iris
 * рисует руки в свои отложенные gbuffer'ы, и попытка скомпоновать поверх ванильного
 * фреймбуфера приводит к тому, что руки пропадают.
 */
public final class IrisCompat {

    private static boolean checked = false;
    private static boolean present = false;
    private static Object apiInstance;
    private static Method isShaderPackInUseMethod;

    private IrisCompat() {}

    private static void init() {
        if (checked) return;
        checked = true;

        boolean modPresent = FabricLoader.getInstance().isModLoaded("iris")
                || FabricLoader.getInstance().isModLoaded("oculus");
        if (!modPresent) return;

        // у разных версий Iris/Oculus отличается пакет API
        String[] apiClasses = {
                "net.irisshaders.iris.api.v0.IrisApi",
                "net.coderbot.iris.api.v0.IrisApi"
        };

        for (String className : apiClasses) {
            try {
                Class<?> apiClass = Class.forName(className);
                Method getInstance = apiClass.getMethod("getInstance");
                apiInstance = getInstance.invoke(null);
                isShaderPackInUseMethod = apiClass.getMethod("isShaderPackInUse");
                present = true;
                return;
            } catch (Throwable ignored) {
                // пробуем следующий вариант
            }
        }
    }

    /** true, если установлен Iris/Oculus и сейчас включён шейдерпак. */
    public static boolean shadersActive() {
        init();
        if (!present) return false;
        try {
            Object result = isShaderPackInUseMethod.invoke(apiInstance);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
