/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 *  net.fabricmc.loader.api.FabricLoader
 */
package ru.white.optimization.velotune.perf;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import ru.white.optimization.velotune.VeloTuneManager;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

@Environment(value=EnvType.CLIENT)
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigManager() {
    }

    public static VeloTuneConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("velotune.json");
        VeloTuneConfig config = new VeloTuneConfig();
        if (Files.isRegularFile(path, new LinkOption[0])) {
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);){
                VeloTuneConfig parsed = (VeloTuneConfig)GSON.fromJson((Reader)reader, VeloTuneConfig.class);
                if (parsed != null) {
                    config = parsed;
                }
            }
            catch (Exception exception) {
                VeloTuneManager.LOGGER.warn("Could not read {}; using safe defaults", (Object)path, (Object)exception);
            }
        }
        config.sanitize();
        ConfigManager.save(path, config);
        return config;
    }

    public static void save(VeloTuneConfig config) {
        config.sanitize();
        ConfigManager.save(FabricLoader.getInstance().getConfigDir().resolve("velotune.json"), config);
    }

    private static void save(Path path, VeloTuneConfig config) {
        try {
            Files.createDirectories(path.getParent(), new FileAttribute[0]);
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, new OpenOption[0]);){
                GSON.toJson((Object)config, (Appendable)writer);
            }
        }
        catch (IOException exception) {
            VeloTuneManager.LOGGER.warn("Could not write {}", (Object)path, (Object)exception);
        }
    }
}
