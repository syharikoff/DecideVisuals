package ru.white.module.impl.render.custompet.model;

import net.minecraft.util.Identifier;
import ru.white.module.impl.render.custompet.entity.CustomPetEntity;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public class RobotModel extends GeoModel<CustomPetEntity> {
    public static final int TYPE_COUNT = 4;
    private static final Identifier MODEL = Identifier.of("wvisual", "robot/robot");
    private static final Identifier ANIMATIONS = Identifier.of("wvisual", "robot/robot");
    private static final Identifier[] TEXTURES = new Identifier[TYPE_COUNT];

    static {
        for (int i = 0; i < TYPE_COUNT; i++) {
            TEXTURES[i] = Identifier.of("wvisual", "textures/entity/robot/type_" + (i + 1) + ".png");
        }
    }

    public RobotModel() {
    }

    @Override
    public Identifier getTextureResource(GeoRenderState geoRenderState) {
        Integer type = geoRenderState.getGeckolibData(CustomPetModel.ROBOT_TYPE);
        int n = type != null ? Math.floorMod(type, TYPE_COUNT) : 0;
        return TEXTURES[Math.floorMod(n, TYPE_COUNT)];
    }

    @Override
    public Identifier getModelResource(GeoRenderState geoRenderState) {
        return MODEL;
    }

    @Override
    public Identifier getAnimationResource(CustomPetEntity customPetEntity) {
        return ANIMATIONS;
    }
}