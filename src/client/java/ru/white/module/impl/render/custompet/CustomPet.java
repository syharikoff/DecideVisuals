package ru.white.module.impl.render.custompet;

import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.impl.render.custompet.control.CustomPetFollowerController;
import ru.white.module.impl.render.custompet.control.DogPetController;
import ru.white.module.impl.render.custompet.entity.CustomPetEntity;
import ru.white.module.impl.render.custompet.render.CustomPetRenderState;
import ru.white.module.impl.render.custompet.render.CustomPetRenderer;
import ru.white.module.impl.render.custompet.render.CustomPetRendererBridge;
import ru.white.module.impl.render.custompet.render.PetRenderCommandQueue;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import ru.white.module.impl.render.custompet.entity.CustomPetEntity;
import software.bernie.geckolib.animation.state.ControllerState;
import software.bernie.geckolib.constant.DataTickets;

@ModuleInfo(
        name = "Custom Pet",
        desc = "Клиентский питомец-компаньон рядом с игроком: жаба, робот или сова",
        category = Category.VISUALS
)
public class CustomPet extends Module {

    private static final String KIND_FROG = "Жаба";
    private static final String KIND_ROBOT = "Робот";
    private static final String KIND_OWL = "Сова";
    private static final String KIND_DOG = "Собака";
    private static final String DOG_TYPE_JACK = "Пёс";
    private static final String DOG_TYPE_DACHSHUND = "Такса";
    private final CustomPetFollowerController localController = new CustomPetFollowerController();
    private final DogPetController dogController = new DogPetController();
    private final BufferAllocator renderAllocator = new BufferAllocator(1 << 20);
    private boolean petPathBroken;

    public ModeSetting petMode = new ModeSetting(this, "Питомец", KIND_FROG, KIND_ROBOT, KIND_OWL, KIND_DOG);
    public ModeSetting petVariant = new ModeSetting(this, "Вид жабы", CustomPetVariant.settingValues());
    public ModeSetting robotType = new ModeSetting(this, "Тип робота", "Робот 1", "Робот 2", "Робот 3", "Робот 4");
    public ModeSetting dogType = new ModeSetting(this, "Порода собаки", DOG_TYPE_JACK, DOG_TYPE_DACHSHUND);

    public CustomPet() {
        petVariant.setVisible(() -> petMode.is(KIND_FROG));
        robotType.setVisible(() -> petMode.is(KIND_ROBOT));
        dogType.setVisible(() -> petMode.is(KIND_DOG));
    }

    @Override
    protected void onEnable() {
        petPathBroken = false;
        CustomPetWarmup.warmup();
    }

    @Override
    protected void onDisable() {
        resetAll();
    }

    @EventHandler
    public void onTick(EventTick event) {
        if (mc.player == null || mc.world == null) {
            resetAll();
            return;
        }
        if (petPathBroken) {
            return;
        }
        try {
            if (isDogSelected()) {
                dogController.tick(mc.player);
                return;
            }
            CustomPetVariant variant = getSelectedVariant();
            boolean owl = isOwlSelected();
            localController.tick(mc.player, variant, owl);
            CustomPetEntity pet = localController.getPet();
            if (pet != null) {
                pet.setRobotType(getSelectedRobotType());
                pet.tick();
            }
        } catch (Throwable throwable) {
            if (!petPathBroken) {
                System.err.println("[PET] tick path broken: " + throwable);
            }
            petPathBroken = true;
        }
    }

    public CustomPetEntity getPet() {
        return this.localController.getPet();
    }

    @EventHandler
    public void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (petPathBroken) {
            return;
        }
        try {
            float tickDelta = event.getTickDelta();
            if (isDogSelected()) {
                dogController.render(event, tickDelta, mc.player, petMode.is(KIND_DOG) && dogType.is(DOG_TYPE_DACHSHUND));
                return;
            }
            CustomPetEntity pet = localController.getPet();
            if (pet == null) {
                return;
            }
            CustomPetRenderer renderer = CustomPetRendererBridge.getPetRenderer();
            if (renderer == null) {
                return;
            }

            CameraRenderState cameraState = buildCameraRenderState(mc.gameRenderer.getCamera());
            CustomPetRenderState state = renderer.createRenderState(pet, null);
            state.light = WorldRenderer.getLightmapCoordinates(mc.world, pet.getBlockPos());
            renderer.updateRenderState(pet, state, tickDelta);

            Vec3d renderPos = interpolateRenderPos(pet, tickDelta);

            VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(renderAllocator);
            PetRenderCommandQueue queue = new PetRenderCommandQueue(immediate);

            event.getMatrixStack().push();
            event.getMatrixStack().translate(
                    renderPos.getX() - cameraState.pos.x,
                    renderPos.getY() - cameraState.pos.y,
                    renderPos.getZ() - cameraState.pos.z
            );
            renderer.render(state, event.getMatrixStack(), queue, cameraState);
            event.getMatrixStack().pop();

            immediate.draw();

            debugAnimationState(state, pet);
        } catch (Throwable throwable) {
            petPathBroken = true;
        }
    }

    private long debugFrameCounter;

    private double prevTickX;
    private double prevTickY;
    private double prevTickZ;
    private double currTickX;
    private double currTickY;
    private double currTickZ;
    private long interpolationTick = -1L;
    private double smoothX;
    private double smoothY;
    private double smoothZ;
    private boolean smoothInitialized;

    private Vec3d interpolateRenderPos(CustomPetEntity pet, float tickDelta) {
        long worldTime = mc.world.getTime();
        if (interpolationTick != worldTime) {
            prevTickX = currTickX;
            prevTickY = currTickY;
            prevTickZ = currTickZ;
            currTickX = pet.getX();
            currTickY = pet.getY();
            currTickZ = pet.getZ();
            interpolationTick = worldTime;
        }
        double renderedX = prevTickX + (currTickX - prevTickX) * tickDelta;
        double renderedY = prevTickY + (currTickY - prevTickY) * tickDelta;
        double renderedZ = prevTickZ + (currTickZ - prevTickZ) * tickDelta;
        if (!smoothInitialized) {
            smoothX = renderedX;
            smoothY = renderedY;
            smoothZ = renderedZ;
            smoothInitialized = true;
        } else {
            double alpha = 0.55;
            smoothX += (renderedX - smoothX) * alpha;
            smoothY += (renderedY - smoothY) * alpha;
            smoothZ += (renderedZ - smoothZ) * alpha;
        }
        if (!pet.isAirborneMode()) {
            double groundY = pet.getGroundSurfaceY(smoothX, smoothY, smoothZ);
            if (smoothY < groundY - 0.1) {
                smoothY = groundY;
            }
        }
        return new Vec3d(smoothX, smoothY, smoothZ);
    }

    private void debugAnimationState(CustomPetRenderState state, CustomPetEntity pet) {
        if ((debugFrameCounter++ % 30L) != 0L) {
            return;
        }
        try {
            ControllerState[] states = null;
            if (state.getGeckolibData(DataTickets.ANIMATION_CONTROLLER_STATES) instanceof ControllerState[] array) {
                states = array;
            }
            String animationName = "?" + (states == null ? "null" : states.length);
            if (states != null && states.length > 0 && states[0].animationPoint() != null && states[0].animationPoint().animation() != null) {
                animationName = states[0].animationPoint().animation().name()
                        + String.format(" t=%.2f", states[0].animationPoint().animTime());
            }
            System.err.printf("[PETDBG] worldTime=%d age=%.2f moving=%b anim=%s animSpeed=%.2f limbProg=%.3f limbAmp=%.3f yaw=%.1f bodyyaw=%.1f pos=(%.2f,%.2f,%.2f)%n",
                    mc.world.getTime(), state.getAnimatableAge(), pet.isPetMoving(), animationName, pet.getCurrentAnimationSpeed(),
                    state.limbSwingAnimationProgress, state.limbSwingAmplitude, pet.getYaw(), pet.getBodyYaw(), pet.getX(), pet.getY(), pet.getZ());
        } catch (Throwable throwable) {
            System.err.println("[PETDBG] error: " + throwable);
        }
    }

    private static CameraRenderState buildCameraRenderState(Camera camera) {
        CameraRenderState cameraState = new CameraRenderState();
        cameraState.initialized = true;
        cameraState.pos = camera.getCameraPos();
        cameraState.blockPos = BlockPos.ofFloored(camera.getCameraPos());
        cameraState.entityPos = camera.getCameraPos();
        cameraState.orientation = camera.getRotation();
        return cameraState;
    }

    private void resetAll() {
        localController.reset();
        dogController.reset();
        petPathBroken = false;
    }

    private boolean isDogSelected() {
        return petMode.is(KIND_DOG);
    }

    private int getSelectedRobotType() {
        return robotType.getIndex();
    }

    private boolean isOwlSelected() {
        return petMode.is(KIND_OWL);
    }

    private CustomPetVariant getSelectedVariant() {
        if (petMode.is(KIND_ROBOT)) {
            return CustomPetVariant.ROBOT;
        }
        if (isDogSelected()) {
            return CustomPetVariant.NITWIT;
        }
        return CustomPetVariant.fromSettingValue(petVariant.getValue());
    }
}