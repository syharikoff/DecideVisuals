package ru.decide;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import ru.decide.command.CommandManager;
import ru.decide.config.ConfigManager;
import ru.decide.cosmetics.CosmeticManager;
import ru.decide.friend.FriendManager;
import ru.decide.inventorypreset.InventoryPresetManager;
import ru.decide.manager.GuiManager;
import ru.decide.manager.events.orbit.EventBus;
import ru.decide.manager.rotation.ComponentManager;
import ru.decide.module.api.ModuleManager;
import ru.decide.optimization.FrameSyncManager;
import ru.decide.optimization.velotune.VeloTuneManager;
import ru.decide.module.impl.utils.VeloTuneModule;
import ru.decide.rpc.RPC;
import ru.decide.screen.Menu;
import ru.decide.utils.render.Render2D;
import ru.decide.utils.render.font.FontInitializer;
import ru.decide.utils.render.lyrics.LyricText3D;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;


import java.lang.invoke.MethodHandles;

@Getter
@Accessors(fluent = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Client implements ClientModInitializer {

    public static Client get;

    public static Client get() {
        return Client.get;
    }

    @Getter
    private static final EventBus eventHandler = EventBus.threadSafe();

    
    public void start() {
        eventHandler.registerLambdaFactory("", (lookupInMethod,
                                                klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));

        FontInitializer.register();
        LyricText3D.registerResourceReload();
    }

    private ModuleManager moduleManager;
    private Render2D render2D;
    private Menu clickGuiScreen;
    private ComponentManager componentManager;
    private CommandManager commandManager;
    private ConfigManager configManager;
    private FriendManager friendManager;
    private InventoryPresetManager inventoryPresetManager;
    final RPC rpc = new RPC();
    public static String build = "5.0";
    private GuiManager guiManager;

    
    @Override
    public void onInitializeClient() {

        System.out.print("Вход");

        FrameSyncManager.applyLwjglTweaks();

        get = this;

        ru.decide.lang.Lang.init();

        start();
        rpc.startRpc();

        this.configManager = new ConfigManager();
        this.configManager.setup();

        this.friendManager = new FriendManager();
        this.friendManager.init();

        this.inventoryPresetManager = new InventoryPresetManager();
        this.inventoryPresetManager.init();

        this.moduleManager = new ModuleManager();
        this.moduleManager.init();

        this.componentManager = new ComponentManager();
        this.componentManager.init();

        this.commandManager = new CommandManager();
        this.commandManager.init();

        this.configManager.init();

        ru.decide.ui.compat.GuiContext.registerDecideVisuals();

        this.guiManager = new GuiManager();
        this.guiManager.init();

        ru.decide.ui.theme.ThemePersistence.init();


        this.render2D = new Render2D();
        this.clickGuiScreen = new Menu();

        Menu.selectedTheme = guiManager.getCurrentTheme();
        Menu.preSelectedTheme = guiManager.getCurrentTheme();

        VeloTuneManager.init();

        VeloTuneModule veloTuneModule = this.moduleManager.get(VeloTuneModule.class);
        if (veloTuneModule != null) {
            veloTuneModule.loadFromConfig();
        }

        // Косметика (порт Lexora): список предметов + выбор из конфига, рендер на модели игрока
        CosmeticManager.getInstance().init();

        // Приветствие при запуске клиента (Effect Sounds). Модуль не обязательно
        // включён - приветствие играет один раз, когда звуковой менеджер готов.
        // Тик берём через Fabric, а не наш EventTick: тот есть только в игре,
        // а приветствие нужно в главном меню.
        final ru.decide.module.impl.utils.EffectSounds effectSounds =
                this.moduleManager.get(ru.decide.module.impl.utils.EffectSounds.class);
        if (effectSounds != null) {
            effectSounds.playGreetingOnStart();
        }
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (effectSounds != null) {
                effectSounds.tickUiSounds();
            }
        });

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
            if (entityRenderer instanceof PlayerEntityRenderer playerRenderer) {
                registrationHelper.register(new ru.decide.cosmetics.CosmeticFeatureRenderer(playerRenderer));
                System.out.println("[DecideVisual Cosmetics] CosmeticFeatureRenderer зарегистрирован");
            } else {
                System.out.println("[DecideVisual Cosmetics] рендерер не PlayerEntityRenderer: " + entityRenderer);
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(this::unload));
    }

    public void unload() {
        if (configManager != null) {
            configManager.autoSave();
        }
    }

}
