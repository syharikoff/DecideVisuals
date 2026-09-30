package ru.white.manager.event_impl;

import ru.white.manager.events.Event;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import java.util.regex.Pattern;

@Setter
@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TextFactoryEvent extends Event {
    String text;

    public void replaceText(String protect, String replaced) {
        if (text == null || text.isEmpty() || protect == null || protect.isEmpty()) return;
        if (text.toLowerCase().contains(protect.toLowerCase())) {
            text = text.replaceAll("(?ui)" + Pattern.quote(protect), replaced);
        }
    }

    public void replaceRegex(String regex, String replaced) {
        if (text == null || text.isEmpty()) return;
        text = text.replaceAll(regex, replaced);
    }

}
