package ru.decide.module.api;

import ru.decide.Client;

import ru.decide.manager.event_impl.EventKey;
import ru.decide.manager.events.orbit.EventHandler;
import ru.decide.module.impl.combat.*;
import ru.decide.module.impl.display.ClickGui;
import ru.decide.module.impl.display.Hud;

import ru.decide.module.impl.display.InterFace;
import ru.decide.module.impl.display.Sounds;
import ru.decide.module.impl.movement.*;
import ru.decide.module.impl.player.*;
import ru.decide.module.impl.render.*;
import ru.decide.module.impl.render.custompet.CustomPet;
import ru.decide.module.impl.utils.*;
import ru.decide.module.impl.utils.VeloTuneModule;



import java.util.*;
import java.util.stream.Collectors;


public final class ModuleManager extends LinkedHashMap<Class<? extends Module>, Module> {


    
    public void init() {

        addSorted(
                new AutoEzz(),
                new NoFriendDamage(),
                new UseTracker(),

                new Sprint(),

                new ClickGui(),
                new EntityEsp(),
                new TargetEsp(),
                new HitWave(),
                new LineGlyphs(),
                new Saturation(),
                new NoRender(),
                new SwingAnimation(),
                new GlassHands(),
                new GlassBlock(),
                new ShaderEsp(),
                new ShaderSky(),
                new ExplosionWave(),
                new ModelCollapse(),
                new BetterMinecraft(),
                new LootView(),
                new WastedDeath(),
                new GlassVapor(),
                new WetWorld(),
                new WorldTweaks(),
                new Gamma(),
                new Particles(),
                new HealthAlert(),
                new ChinaHat(),
                new JumpCircle(),
                new HitRange(),
                new Emotions(),
                new JumpCube(),
                new CrossHair(),
                new Trails(),
                new WorldCubes(),
                new Trajectories(),
                new ColorGrade(),
                new FireFlies(),
                new VoidButterflies(),
                new Svetoch(),
                new ScanWorld(),
                new WorldTweaks(),
                new CustomPet(),
                new TotemGhost(),
                new KillEffect(),
                new LyricsText(),
                new Hands(),
                new HoldMyItems(),
                new InterFace(),
                new Sounds(),
                new CustomSword(),
                new AutoAccept(),
                new NoDelay(),
                new LockSlot(),
                new FreeLook(),
                new PvpSafe(),
                new NameProtect(),
                new ItemScroller(),
                new AutoInvest(),
                new FakePlayer(),
                new ItemHighlight(),
                new ConsumableOptimizer(),
                new FrameSync(),
                new AutoSwapModule(),
                new ru.decide.module.impl.utils.SpearHelper(),
                new ru.decide.module.impl.utils.EffectSounds(),
                new ru.decide.module.impl.utils.SoundsKey(),
                new CoordInvite(),
                new AutoFixCommand(),
                new VeloTuneModule(),
                new ru.decide.module.impl.utils.SoundController(),
                new Optimizer()
        );

        this.values().stream()
                .filter(Module::isAutoEnabled)
                .forEach(module -> module.setEnabled(true, false));

        Client.eventHandler().subscribe(this);
    }

    public void addSorted(Module... modules) {
        Arrays.stream(modules)
                .forEach(module -> this.put(module.getClass(), module));
    }

    public void unregister(Module... modules) {
        Arrays.stream(modules).forEach(module -> this.remove(module.getClass()));
    }

    @EventHandler
    public void onKeyboardPress(EventKey event) {
            this.values().stream()
                    .filter(module -> module.getKey() == event.getKey())
                    //.filter(module -> !(module instanceof ClickGui))
                    .forEach(Module::toggle);
    }



    public <T extends Module> T get(final String name) {
        return this.values().stream()
                .filter(module -> module.getName().equalsIgnoreCase(name))
                .map(module -> (T) module)
                .findFirst()
                .orElse(null);
    }


    public <T extends Module> T get(final Class<T> clazz) {
        return this.values().stream()
                .filter(module -> clazz.isAssignableFrom(module.getClass()))
                .map(clazz::cast)
                .findFirst()
                .orElse(null);
    }


    public List<Module> get(final Category category) {
        return this.values().stream()
                .filter(module -> module.getCategory() == category)
                .collect(Collectors.toList());
    }


    public Module getModule(String name) {
        return this.values().stream()
                .filter(module -> module.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    @Override
    public Collection<Module> values() {
        return super.values().stream()
                .sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }
}
