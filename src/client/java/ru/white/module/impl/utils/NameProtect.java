package ru.white.module.impl.utils;

import ru.white.Client;
import ru.white.manager.event_impl.TextFactoryEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;

@ModuleInfo(
        name = "Name Protect",
        category = Category.UTILITIES,
        desc = "Скрывает ваш никнейм"
)
public class NameProtect extends Module {

    public BooleanSetting friends = new BooleanSetting(this,"Скрывать друзей",true);
    public BooleanSetting anarhy = new BooleanSetting(this,"Анархию",false);

    @EventHandler
    public void onEvent(TextFactoryEvent e) {

        if(anarhy.getValue())
        e.replaceRegex("(?ui)Анархия-(?:(?:1\\d{3})|(?:[1-9]\\d{0,2})|2000)", "decidevisuals.fun");

        e.replaceText(mc.getSession().getUsername(), "decidevisuals");

        e.replaceRegex("funtime", "Успешный проект");


        e.replaceRegex("FunTime.su", "Успешный проект");
        e.replaceRegex("Анархия", "HvH");



        if (friends.getValue()) {
            replaceFriendNames(e);
        }
    }

    private void replaceFriendNames(TextFactoryEvent e) {
        Client.get().friendManager().getFriends().forEach(friend -> e.replaceText(friend, "Friend"));
    }

}
