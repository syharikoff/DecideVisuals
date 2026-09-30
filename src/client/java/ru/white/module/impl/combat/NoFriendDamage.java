package ru.white.module.impl.combat;

import ru.white.Client;
import ru.white.manager.event_impl.AttackEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;

@ModuleInfo(
        name = "No Friend Damage",
        desc = "Убирает удары по друзьям",
        category = Category.UTILITIES
)
public class NoFriendDamage extends Module {


    @EventHandler
    public void onEvent(AttackEvent event) {

        if(Client.get().friendManager().isFriend(event.getTarget().getName().getString())) {
            event.cancel();
        }

    }


}
