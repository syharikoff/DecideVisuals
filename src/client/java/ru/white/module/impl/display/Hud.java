package ru.white.module.impl.display;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import ru.white.Client;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.module.api.settings.impl.*;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.*;
import ru.white.theme.ThemeColor;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.animation.satoshi.Direction;
import ru.white.utils.animation.satoshi.EaseInOutQuad;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.Keyboard;
import ru.white.utils.math.MathUtil;
import ru.white.utils.math.ServerUtil;
import ru.white.utils.other.Instance;
import ru.white.utils.render.GifTexture;
import ru.white.utils.render.ItemRender;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.font.Fonts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.MathHelper;

import java.awt.*;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@ModuleInfo(
        name = "Hud",
        desc = "Настройка интерфейса (HUD) клиента",
        category = Category.HUD
)
public class Hud extends Module {


    public BooleanSetting waterMark = new BooleanSetting("Лого клиента", true);

    public BooleanSetting cortds = new BooleanSetting("Координаты", true);

    public BooleanSetting bpstps = new BooleanSetting("BPS и TPS", true);

    public BooleanSetting porionList = new BooleanSetting("Активные эффекты", true);

    public BooleanSetting keybindslist = new BooleanSetting("Активные клавиши", true);

    public BooleanSetting arrayListHude = new BooleanSetting("Аррай лист", false);
    public BooleanSetting armorHud = new BooleanSetting("Armor", true);
    public BooleanSetting inventoryHud = new BooleanSetting("Inventory", true);


    public MultiBooleanSetting elempt = new MultiBooleanSetting(this, "Отображать", waterMark, keybindslist, porionList, bpstps, cortds, arrayListHude, armorHud, inventoryHud);


    public SliderSetting volume = new SliderSetting(this, "Громкость уведомления", 0.5F, 0.1F, 1.0F, 0.1F);
    public ModeSetting typeNotify = new ModeSetting(this, "Тип уведомления",
            "Первый", "Второй", "Третий");


    public Animation withpan = new Animation();


    public static boolean getShadow() {
        return false;
    }


    public static int getVisualsColor() {


        return ThemeColor.getVisualColor();
    }

    public static int getHudColor() {


        return ThemeColor.getHudColor();
    }

    public DragSetting watermark_drag = new DragSetting(this, "watermark_draggings");
    public DragSetting keybinds_drag = new DragSetting(this, "Keybinds_dragings");
    public DragSetting potions_drag = new DragSetting(this, "Potions_dragings");
    public DragSetting clickbinds_drag = new DragSetting(this, "ClickBinds_dragings", new org.joml.Vector2f(8, 100));
    public DragSetting arraylist_drag = new DragSetting(this, "ArrayList_dragings", new org.joml.Vector2f(10000, 4));
    public DragSetting armor_drag = new DragSetting(this, "Armor_dragings", new org.joml.Vector2f(160, 90));
    public DragSetting inventory_drag = new DragSetting(this, "Inventory_dragings", new org.joml.Vector2f(160, 170));

    public DragSetting cords_drag = new DragSetting(this, "Cords_dragings", new org.joml.Vector2f(-999, -999));
    public DragSetting bps_drag = new DragSetting(this, "Bps_dragings", new org.joml.Vector2f(-999, -999));
    public DragSetting tps_drag = new DragSetting(this, "Tps_dragings", new org.joml.Vector2f(-999, -999));

    public static float getAlpha() {
        return ThemeColor.getOpacity() < 0.98f ? ThemeColor.getOpacity() : 0.98F;
    }

    private float smoothMaxKeyWidth = -1f;
    public Animation mainAnim = new Animation();

    private static class EffectData {
        String name;
        String duration;
        Animation animation;
        boolean active;
        boolean negative;
        int durationTicks;
        int lvl;
        StatusEffectInstance effectInstance;

        public EffectData(String name, String duration, int lvl, boolean negative, int durationTicks, StatusEffectInstance effectInstance) {
            this.name = name;
            this.duration = duration;
            this.lvl = lvl;
            this.negative = negative;
            this.durationTicks = durationTicks;
            this.animation = new Animation();
            this.active = true;
            this.effectInstance = effectInstance;
        }
    }

    public Animation keybindsWidthAnim = new Animation();
    public Animation keybindsMaxKeyWidthAnim = new Animation();
    private final Map<String, EffectData> displayedEffects = new LinkedHashMap<>();

    ru.white.utils.animation.satoshi.Animation animation = new EaseInOutQuad(200, 1);
    public MinecraftClient mc = MinecraftClient.getInstance();

    private double prevX, prevY, prevZ;
    private long prevTime;
    private float bps;

    private GifTexture testGif;
    private boolean gifLoaded = false;
}
