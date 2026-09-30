package ru.white.module.impl.player;

import com.mojang.authlib.GameProfile;
import ru.white.Client;
import ru.white.manager.event_impl.AttackEvent;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.impl.render.TotemGhost;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(
        name = "Fake Player",
        desc = "Спавнит клиентского фейк-игрока для битья/теста",
        category = Category.UTILITIES
)
public class FakePlayer extends Module {

    private static final String[] NAMES = {
            "Steve", "Alex", "Herobrine", "Nagibator", "Vitalik", "Sanya", "Dimon",
            "Leha", "KolyaPRO", "Artem", "Nikita", "Timoha", "Zhenya", "MaksFX", "Vladik"
    };
    private static final float WALK_RADIUS = 2.0F;

    public BooleanSetting walk = new BooleanSetting(this, "Ходьба", true);
    public BooleanSetting rotate = new BooleanSetting(this, "Поворот к игроку", true);
    public BooleanSetting totem = new BooleanSetting(this, "Тотем", false);

    private OtherClientPlayerEntity fakePlayer;
    private Vec3d spawnPosition = Vec3d.ZERO;
    private Vec3d walkAxis = Vec3d.ZERO;
    private float spawnYaw;
    private float walkPhase;
    private int hitsSinceTotem;

    @Override
    protected void onDisable() {
        despawn();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        despawn();
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (mc.player == null || mc.world == null) {
            fakePlayer = null;
            return;
        }
        if (fakePlayer == null || fakePlayer.isRemoved()) {
            despawn();
            spawn();
        }
        syncTotem();
        move();
    }

    private void spawn() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String name = NAMES[random.nextInt(NAMES.length)] + random.nextInt(10, 100);
        OtherClientPlayerEntity player = new OtherClientPlayerEntity(mc.world, new GameProfile(UUID.randomUUID(), name));
        player.setId(-random.nextInt(1000000, 2000000));

        spawnPosition = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        spawnYaw = mc.player.getYaw();
        float rad = spawnYaw * (float) (Math.PI / 180.0);
        walkAxis = new Vec3d(-MathHelper.cos(rad), 0.0, -MathHelper.sin(rad));
        walkPhase = 0.0F;

        player.refreshPositionAndAngles(spawnPosition.x, spawnPosition.y, spawnPosition.z, spawnYaw, 0.0F);
        player.setHeadYaw(spawnYaw);
        player.setBodyYaw(spawnYaw);
        player.setHealth(player.getMaxHealth());
        mc.world.addEntity(player);

        fakePlayer = player;
        hitsSinceTotem = 0;
        syncTotem();
    }

    private void move() {
        if (fakePlayer == null) return;

        Vec3d vec = spawnPosition;
        float yaw = spawnYaw;
        float pitch = 0.0F;

        if (walk.getValue()) {
            walkPhase += 0.1F;
            vec = spawnPosition.add(walkAxis.multiply(MathHelper.sin(walkPhase) * WALK_RADIUS));
            double dir = MathHelper.cos(walkPhase) >= 0.0 ? 1.0 : -1.0;
            Vec3d axis = walkAxis.multiply(dir);
            yaw = (float) Math.toDegrees(Math.atan2(-axis.x, axis.z));
        }

        if (rotate.getValue() && mc.player != null) {
            double dx = mc.player.getX() - fakePlayer.getX();
            double dy = mc.player.getEyeY() - fakePlayer.getEyeY();
            double dz = mc.player.getZ() - fakePlayer.getZ();
            yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
            pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        }

        fakePlayer.getInterpolator().refreshPositionAndAngles(vec, yaw, pitch);
        fakePlayer.setHeadYaw(yaw);
    }

    private void syncTotem() {
        if (fakePlayer == null) return;
        fakePlayer.equipStack(EquipmentSlot.OFFHAND,
                totem.getValue() ? new ItemStack(Items.TOTEM_OF_UNDYING) : ItemStack.EMPTY);
        if (!totem.getValue()) {
            hitsSinceTotem = 0;
            fakePlayer.setHealth(fakePlayer.getMaxHealth());
        }
    }

    private void despawn() {
        if (fakePlayer != null) {
            fakePlayer.discard();
            fakePlayer = null;
        }
    }

    public boolean isFakePlayer(Entity entity) {
        return fakePlayer != null && entity == fakePlayer;
    }

    @EventHandler
    public void onAttack(AttackEvent event) {
        if (fakePlayer == null || event.getTarget() != fakePlayer) return;
        event.cancel();
        if (mc.player == null) return;

        mc.player.swingHand(Hand.MAIN_HAND);
        spawnHitParticles();

        if (totem.getValue()) {
            if (++hitsSinceTotem >= 3) {
                triggerTotemPop();
            } else {
                fakePlayer.setHealth(Math.max(1.0F, fakePlayer.getMaxHealth() * (3 - hitsSinceTotem) / 3.0F));
            }
        }
    }

    private void triggerTotemPop() {
        hitsSinceTotem = 0;
        fakePlayer.setHealth(1.0F);

        mc.world.addParticleClient(ParticleTypes.TOTEM_OF_UNDYING, true, true,
                fakePlayer.getX(), fakePlayer.getY() + 1.0, fakePlayer.getZ(),
                0.0, 0.15, 0.0);
        mc.world.playSoundClient(
                fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(),
                SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0F, 1.0F, false);

        Module ghost = Client.get().moduleManager().get(TotemGhost.class);
        if (ghost instanceof TotemGhost totemGhost) {
            totemGhost.spawnGhost(fakePlayer);
        }

        fakePlayer.setHealth(fakePlayer.getMaxHealth());
    }

    private void spawnHitParticles() {
        if (mc.player == null) return;
        double damage = mc.player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
        int count = Math.max(1, (int) (damage * 0.5));
        for (int i = 0; i < count; i++) {
            mc.world.addParticleClient(ParticleTypes.DAMAGE_INDICATOR, true, true,
                    fakePlayer.getX(), fakePlayer.getY() + 0.5, fakePlayer.getZ(),
                    mc.world.random.nextGaussian() * 0.1, 0.0, mc.world.random.nextGaussian() * 0.1);
        }
    }
}