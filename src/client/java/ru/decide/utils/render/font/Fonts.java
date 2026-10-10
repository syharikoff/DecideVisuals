package ru.decide.utils.render.font;

import java.util.LinkedHashMap;
import java.util.Map;

public class Fonts {

    private static final Map<String, String> FONT_REGISTRY = new LinkedHashMap<>();



    public static final Font sf_bold = register("sf_bold", "sf_bold");
    public static final Font sf_medium = register("sf_medium", "sf_medium");
    public static final Font sf_regular = register("sf_regular", "sf_regular");

    public static final Font icon = register("decide", "decide");
    public static final Font category = register("category", "category");
    public static final Font gui = register("icongui", "icongui");
    public static final Font decide_2 = register("decide_2", "decide_2");

    // Для Lyrics Text: Manasco и SF Pro Medium перенесены из Kimiko,
    // чтобы визуал совпадал один в один.
    public static final Font manasco = register("manasco", "manasco");
    public static final Font sf_pro_medium = register("sf_pro_medium", "sf_pro_medium");



    private static Font register(String name, String path) {
        FONT_REGISTRY.put(name, path);
        return new Font(name);
    }

    public static Map<String, String> getRegistry() {
        return FONT_REGISTRY;
    }

    private Fonts() {}
}