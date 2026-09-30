package ru.white.module.impl.render.custompet.model;

import net.minecraft.util.Identifier;
import ru.white.module.impl.render.custompet.entity.CustomPetEntity;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public class FrogModel extends GeoModel<CustomPetEntity> {
    private static final Identifier MODEL = Identifier.of("wvisual", "frog/custom_pet");
    private static final Identifier TEXTURE = Identifier.of("wvisual", "textures/entity/frog/custom_pet.png");
    private static final Identifier ANIMATIONS = Identifier.of("wvisual", "frog/custom_pet");

    public FrogModel() {
    }

    @Override
    public Identifier getTextureResource(GeoRenderState geoRenderState) {
        return TEXTURE;
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