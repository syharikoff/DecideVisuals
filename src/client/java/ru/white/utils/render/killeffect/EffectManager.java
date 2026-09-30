package ru.white.utils.render.killeffect;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class EffectManager {
    private final List<ActiveEffect> active = new ArrayList<>();

    public void spawn(EffectCatalog.Effect effect, Vec3d position, float spawnYaw, Identifier skinTexture) {
        active.add(new ActiveEffect(effect, position, System.currentTimeMillis(), spawnYaw, skinTexture));
    }

    public List<ActiveEffect> active() {
        Iterator<ActiveEffect> it = active.iterator();
        while (it.hasNext()) {
            if (!it.next().isFinished()) continue;
            it.remove();
        }
        return active;
    }

    public record ActiveEffect(EffectCatalog.Effect effect, Vec3d position, long startMillis, float spawnYaw, Identifier skinTexture) {
        public float timeSeconds() {
            return (float)(System.currentTimeMillis() - startMillis) / 1000.0f;
        }

        public boolean isFinished() {
            return (double) timeSeconds() > effect.duration();
        }
    }
}
