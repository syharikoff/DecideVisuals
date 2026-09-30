package ru.white.utils.render.shader;

import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Раздаёт GLSL-исходники из зашифрованных Java-констант ({@link ShaderData}) вместо
 * файлов в ресурс-паке. {@code ShaderLoaderMixin} перехватывает
 * {@code ShaderLoader.getSource(Identifier, ShaderType)} и для namespace "client"
 * берёт исходник отсюда.
 *
 * Шифрование (XOR + Base64) — это только сокрытие от grep/strings по jar, не криптозащита.
 * Ключ ОБЯЗАН совпадать с tools/GenShaderData.java.
 */
public final class ShaderStore {

    private ShaderStore() {}

    /** Должно совпадать с GenShaderData.KEY */
    private static final byte[] KEY = "Nightix//shader-veil//2026".getBytes(StandardCharsets.UTF_8);

    /** namespace, в котором лежат наши шейдеры (assets/client/...) */
    private static final String NAMESPACE = "client";

    private static final String INCLUDE_DIR = "shaders/include/";

    /**
     * @return полностью готовый (с разрешёнными #moj_import) GLSL-исходник
     *         для данного шейдера, либо {@code null} если это не наш шейдер —
     *         тогда движок грузит его штатно.
     */
    public static String getSource(Identifier id, ShaderType type) {
        if (id == null || !NAMESPACE.equals(id.getNamespace())) return null;

        String ext = (type == ShaderType.VERTEX) ? "vsh" : "fsh";
        String key = id.getPath() + "|" + ext;          // напр. "core/rect|vsh"

        String enc = ShaderData.SOURCES.get(key);
        if (enc == null) return null;

        String raw = decrypt(enc);
        // owner в форме ресурса — нужен только для относительных #moj_import "..."
        Identifier owner = Identifier.of(id.getNamespace(), "shaders/" + id.getPath() + "." + ext);
        String resolved = resolveImports(raw, owner, new HashSet<>());
        // Ванильные include'ы (dynamictransforms.glsl, projection.glsl) сами содержат
        // #version 330 — как и GlImportProcessor, оставляем только ОДИН #version наверху,
        // иначе GLSL не компилируется (несколько #version) и весь reload падает.
        return dedupeVersion(resolved);
    }

    /** Оставляет только первую директиву #version, остальные удаляет (как extractVersion в ванилле). */
    private static String dedupeVersion(String source) {
        StringBuilder out = new StringBuilder(source.length());
        boolean versionSeen = false;
        for (String line : source.split("\n", -1)) {
            if (line.trim().startsWith("#version")) {
                if (versionSeen) continue;   // дубликат — выкидываем
                versionSeen = true;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    private static String decrypt(String base64) {
        byte[] data = Base64.getDecoder().decode(base64);
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) (data[i] ^ KEY[i % KEY.length]);
        }
        return new String(out, StandardCharsets.UTF_8);
    }

    /**
     * Минимальная замена GlImportProcessor: инлайнит #moj_import, читая include-ресурсы
     * из ResourceManager (так ванильные includes остаются version-correct). Каждый
     * уникальный include подставляется один раз — как в ванилле.
     */
    private static String resolveImports(String source, Identifier owner, Set<String> seen) {
        StringBuilder out = new StringBuilder(source.length() + 256);
        for (String line : source.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#moj_import")) {
                Identifier inc = parseImport(trimmed, owner);
                if (inc != null) {
                    if (seen.add(inc.toString())) {
                        String incSrc = readResource(inc);
                        if (incSrc != null) {
                            out.append(resolveImports(incSrc, inc, seen));
                            if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                                out.append('\n');
                            }
                        }
                    }
                    continue; // строку #moj_import в вывод не пишем
                }
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    /** Превращает строку #moj_import в Identifier include-ресурса (shaders/include/...). */
    private static Identifier parseImport(String line, Identifier owner) {
        int lt = line.indexOf('<');
        int gt = line.indexOf('>');
        if (lt >= 0 && gt > lt) {
            // абсолютный namespaced импорт: <ns:file.glsl>
            String ref = line.substring(lt + 1, gt).trim();
            Identifier id = Identifier.of(ref);
            return Identifier.of(id.getNamespace(), INCLUDE_DIR + id.getPath());
        }
        int q1 = line.indexOf('"');
        int q2 = line.lastIndexOf('"');
        if (q1 >= 0 && q2 > q1) {
            // относительный импорт: "file.glsl" — относительно каталога owner'а
            String ref = line.substring(q1 + 1, q2).trim();
            String ownerPath = owner.getPath();
            int slash = ownerPath.lastIndexOf('/');
            String dir = slash >= 0 ? ownerPath.substring(0, slash + 1) : "";
            return Identifier.of(owner.getNamespace(), dir + ref);
        }
        return null;
    }

    private static String readResource(Identifier id) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) return null;
            ResourceManager rm = mc.getResourceManager();
            if (rm == null) return null;
            Optional<Resource> res = rm.getResource(id);
            if (res.isEmpty()) return null;
            try (InputStream in = res.get().getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
