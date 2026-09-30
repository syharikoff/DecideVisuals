package ru.white.cosmetics.render;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.Identifier;
import ru.white.cosmetics.model.GeoModel;

@Getter
@Setter
public class CosmeticModelData {
    private final int id;
    private final String name;
    private final GeoModel model;
    private final Identifier texture;

    private float scale = 1.0F;
    private float x = 0.0F;
    private float y = 0.0F;
    private float z = 0.0F;
    private float yaw = 0.0F;
    private float pitch = 0.0F;
    private float roll = 0.0F;

    private float previewScale = 1.0F;
    private float previewY = 0.0F;

    public CosmeticModelData(int id, String name, GeoModel model, Identifier texture) {
        this.id = id;
        this.name = name;
        this.model = model;
        this.texture = texture;
    }
}
