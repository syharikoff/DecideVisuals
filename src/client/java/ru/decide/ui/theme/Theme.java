package ru.decide.ui.theme;

import java.awt.Color;

public enum Theme {
    DECIDEVISUALS("Клиентская", new int[]{9081843, 11838975}),
    BLUEPINK("Голубо-розовая", new int[]{4776676, 14045679}),
    BLUEGREEN("Сине-зелёная", new int[]{911192, 157920}),
    DARKBLUE("Тёмно-синяя", new int[]{4023774, 2166617}),
    POLARIZE("Чёрно-белая", new int[]{13158600, 8092539}),
    WATER("Водяная", new int[]{6134508, 737480}),
    VIOLET("Фиолетовая", new int[]{9443736, 4395888}),
    LILAC("Лавандовая", new int[]{12618202, 6892934}),
    SUNRISE("Рассветная", new int[]{16087094, 14039456}),
    SUNSET("Закатная", new int[]{16742912, 9317858}),
    SPRING("Весенняя", new int[]{11067491, 5679919}),
    SUMMER("Летняя", new int[]{16769625, 16754513}),
    WINTER("Зимняя", new int[]{14740220, 13623027}),
    MIDNIGHT("Полуночная", new int[]{991271, 2904932}),
    HALLOWEEN("Хэллоуинская", new int[]{16741656, 1710618}),
    NEWYEAR("Новогодняя", new int[]{1981554, 12597547}),
    VALENTINE("Влюблённая", new int[]{16739229, 12862825}),
    FIRE("Огненная", new int[]{16765440, 16711680}),
    EARTH("Земляная", new int[]{9132587, 4073251}),
    ICE("Ледяная", new int[]{10616782, 46299}),
    FOREST("Лесная", new int[]{1265171, 3046706}),
    GALAXY("Космическая", new int[]{986153, 9055202}),
    DESERT("Пустынная", new int[]{15254430, 12092939}),
    GOLD("Золотая", new int[]{16774839, 12092939}),
    EMERALD("Изумрудная", new int[]{5294200, 222768}),
    CORAL("Коралловая", new int[]{16744272, 15287402}),
    MINT("Мятная", new int[]{11993051, 2074234}),
    PASTEL("Пастельная", new int[]{16765404, 12710128}),
    TEAL("Бирюзовая", new int[]{1022862, 23639}),
    BLOODY("Кровавая", new int[]{12138082, 6757403}),
    NEON("Неоновая", new int[]{16720128, 59903}),
    AUTUMN("Осенняя", new int[]{59903, 12601856}),
    CHRISTMAS("Рождественская", new int[]{14617355, 14470091}),
    REVOLUT("Неоновая революция", new int[]{5034078, 7881892}),
    WATERMEL("Арбузная", new int[]{13453107, 10796658});

    private final String displayName;
    private final int accent;
    private final int accentBright;
    private final int accentSoft;
    private final int accentFill;
    private final int toggleOn;
    private final int gradientA;
    private final int gradientB;
    private final int[] palette;

    private Theme(String displayName, int[] palette) {
        this.displayName = displayName;
        int size = Math.max(1, palette.length);
        int[] clean = new int[size];

        for (int i = 0; i < size; i++) {
            clean[i] = palette[i] & 16777215;
        }

        this.palette = clean;
        int base = clean[0];
        this.accent = base;
        this.accentBright = lighten(base, 0.65F, 1.15F, 0.1F);
        this.accentSoft = lighten(base, 0.45F, 1.25F, 0.15F);
        this.accentFill = darken(base, 1.05F, 0.78F);
        this.toggleOn = darken(base, 1.1F, 0.55F);
        this.gradientA = base;
        this.gradientB = clean[size - 1];
    }

    public String displayName() {
        return this.displayName;
    }

    public int[] shades() {
        return new int[]{this.accent, this.accentBright, this.accentSoft, this.accentFill, this.toggleOn, this.gradientA, this.gradientB};
    }

    public int accentRgb() {
        return this.accent;
    }

    private static float[] toHsb(int color) {
        float[] hsb = new float[3];
        Color.RGBtoHSB(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, hsb);
        return hsb;
    }

    public int gradientA() {
        return this.gradientA;
    }

    public int gradientB() {
        return this.gradientB;
    }

    private static float clamp01(float f) {
        return f < 0.0F ? 0.0F : (f > 1.0F ? 1.0F : f);
    }

    private static int darken(int color, float satMul, float briMul) {
        float[] hsb = toHsb(color);
        return Color.HSBtoRGB(hsb[0], clamp01(hsb[1] * satMul), hsb[2] * briMul) & 16777215;
    }

    public int[] palette() {
        return this.palette;
    }

    private static int lighten(int color, float satMul, float briMul, float briAdd) {
        float[] hsb = toHsb(color);
        return Color.HSBtoRGB(hsb[0], clamp01(hsb[1] * satMul), clamp01(hsb[2] * briMul + briAdd)) & 16777215;
    }

    public int accentSoftRgb() {
        return this.accentSoft;
    }

    public int accentFillRgb() {
        return this.accentFill;
    }

    public int accentBrightRgb() {
        return this.accentBright;
    }

    public int toggleOnRgb() {
        return this.toggleOn;
    }
}