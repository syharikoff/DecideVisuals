package ru.white.module.impl.render.custompet.model;

import net.minecraft.util.Identifier;
import ru.white.module.impl.render.custompet.entity.CustomPetEntity;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public class OwlModel extends GeoModel<CustomPetEntity> {
    private static final Identifier MODEL = Identifier.of("wvisual", "owl/owl_jump_rope");
    private static final Identifier TEXTURE = Identifier.of("wvisual", "textures/entity/owl/owl_jump_rope.png");
    private static final Identifier ANIMATIONS = Identifier.of("wvisual", "owl/owl_jump_rope");

    public OwlModel() {
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