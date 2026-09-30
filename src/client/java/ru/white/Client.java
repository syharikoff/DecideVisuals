package ru.white;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import ru.white.command.CommandManager;
import ru.white.config.ConfigManager;
import ru.white.cosmetics.CosmeticManager;
import ru.white.cosmetics.GraffitiManager;
import ru.white.cosmetics.render.NightixCosmeticsFeatureRenderer;
import ru.white.cosmetics.render.PetWorldRenderer;
import ru.white.friend.FriendManager;
import ru.white.inventorypreset.InventoryPresetManager;
import ru.white.manager.GuiManager;
import ru.white.manager.events.orbit.EventBus;
import ru.white.manager.rotation.ComponentManager;
import ru.white.module.api.ModuleManager;
import ru.white.optimization.FrameSyncManager;
import ru.white.optimization.velotune.VeloTuneManager;
import ru.white.module.impl.utils.VeloTuneModule;
import ru.white.rpc.RPC;
import ru.white.screen.Menu;
import ru.white.utils.render.Render2D;
import ru.white.utils.render.font.FontInitializer;
import ru.white.utils.render.lyrics.LyricText3D;
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

        ru.white.lang.Lang.init();

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

        ru.white.ui.compat.GuiContext.registerDecideVisuals();

        this.guiManager = new GuiManager();
        this.guiManager.init();

        ru.white.ui.theme.ThemePersistence.init();


        this.render2D = new Render2D();
        this.clickGuiScreen = new Menu();

        Menu.selectedTheme = guiManager.getCurrentTheme();
        Menu.preSelectedTheme = guiManager.getCurrentTheme();

        VeloTuneManager.init();

        VeloTuneModule veloTuneModule = this.moduleManager.get(VeloTuneModule.class);
        if (veloTuneModule != null) {
            veloTuneModule.loadFromConfig();
        }

        // Косметика: выбор с диска, фоновый прогрев моделей, рендер питомцев и граффити
        CosmeticManager.get().load();
        CosmeticManager.get().warmUpAsync();
        GraffitiManager.load();
        PetWorldRenderer.register();
        GraffitiManager.register();

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
            if (entityRenderer instanceof PlayerEntityRenderer playerRenderer) {
                registrationHelper.register(new NightixCosmeticsFeatureRenderer(playerRenderer));
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
