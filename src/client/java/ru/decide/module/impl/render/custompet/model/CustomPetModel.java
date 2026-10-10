package ru.decide.module.impl.render.custompet.model;

import net.minecraft.util.Identifier;
import ru.decide.module.impl.render.custompet.CustomPetVariant;
import ru.decide.module.impl.render.custompet.entity.CustomPetEntity;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public class CustomPetModel extends GeoModel<CustomPetEntity> {
    public static final DataTicket<Boolean> UMBRELLA = DataTicket.create("decidevisuals_custom_pet.umbrella", Boolean.class);
    public static final DataTicket<Boolean> AIRBORNE = DataTicket.create("decidevisuals_custom_pet.airborne", Boolean.class);
    public static final DataTicket<String> VARIANT = DataTicket.create("decidevisuals_custom_pet.variant", String.class);
    public static final DataTicket<Integer> ROBOT_TYPE = DataTicket.create("decidevisuals_custom_pet.robot_type", Integer.class);
    public static final DataTicket<Boolean> OWL = DataTicket.create("decidevisuals_custom_pet.owl", Boolean.class);
    private final FrogModel frog = new FrogModel();
    private final RobotModel robot = new RobotModel();
    private final OwlModel owl = new OwlModel();

    public CustomPetModel() {
    }

    @SuppressWarnings("unchecked")
    private GeoModel<CustomPetEntity> pick(GeoRenderState geoRenderState) {
        if (Boolean.TRUE.equals(geoRenderState.getGeckolibData(OWL))) {
            return this.owl;
        } else {
            return (GeoModel<CustomPetEntity>) (CustomPetVariant.ROBOT.name().equals(geoRenderState.getGeckolibData(VARIANT)) ? this.robot : this.frog);
        }
    }

    @Override
    public void addAdditionalStateData(CustomPetEntity customPetEntity, Object relatedObject, GeoRenderState geoRenderState) {
        geoRenderState.addGeckolibData(UMBRELLA, customPetEntity.shouldUseUmbrella());
        geoRenderState.addGeckolibData(AIRBORNE, customPetEntity.isAirborneMode());
        geoRenderState.addGeckolibData(VARIANT, customPetEntity.getPetVariant().name());
        geoRenderState.addGeckolibData(ROBOT_TYPE, customPetEntity.getRobotType());
        geoRenderState.addGeckolibData(OWL, customPetEntity.isOwl());
    }

    @Override
    public Identifier getTextureResource(GeoRenderState geoRenderState) {
        return this.pick(geoRenderState).getTextureResource(geoRenderState);
    }

    @Override
    public Identifier getModelResource(GeoRenderState geoRenderState) {
        return this.pick(geoRenderState).getModelResource(geoRenderState);
    }

    @Override
    public Identifier getAnimationResource(CustomPetEntity customPetEntity) {
        if (customPetEntity.isOwl()) {
            return this.owl.getAnimationResource(customPetEntity);
        } else {
            return customPetEntity.getPetVariant().isRobot() ? this.robot.getAnimationResource(customPetEntity) : this.frog.getAnimationResource(customPetEntity);
        }
    }
}