package ru.white.cosmetics;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.util.Identifier;

@Getter
@RequiredArgsConstructor
public class CosmeticItem {
    private final int id;
    private final CosmeticCategory category;
    private final String name;
    private final Identifier preview;

    public CosmeticItem(int id, CosmeticCategory category, String name) {
        this.id = id;
        this.category = category;
        this.name = name;
        this.preview = Identifier.of("client", "textures/cosmetics/" + category.getFolder() + "/" + id + ".png");
    }
}
