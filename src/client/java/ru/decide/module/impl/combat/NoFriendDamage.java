package ru.decide.module.impl.combat;

import ru.decide.Client;
import ru.decide.manager.event_impl.AttackEvent;
import ru.decide.manager.events.orbit.EventHandler;
import ru.decide.module.api.Category;
import ru.decide.module.api.Module;
import ru.decide.module.api.ModuleInfo;

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
