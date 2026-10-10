package ru.decide.utils.math;


import ru.decide.utils.annotation.IMinecraft;
import ru.decide.utils.colors.ColorUtil;
import lombok.experimental.UtilityClass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.awt.*;

@UtilityClass
public class ChatUtils implements IMinecraft {


    public void addChatMessage(String text) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.inGameHud == null || mc.inGameHud.getChatHud() == null) {
            System.out.println("[Nightly Logs] " + text);
            return;
        }

        Text message = Text.literal(    "").append(gradientText("DecideVisuals", new Color(ColorUtil.fade(90)),new Color(ColorUtil.fade(180)))).append(Text.literal(" » ").formatted(Formatting.GRAY)).append(Text.literal(text).formatted(Formatting.WHITE));

        mc.inGameHud.getChatHud().addMessage(message);
    }



    public void addChatMessageDev(String text) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.inGameHud == null || mc.inGameHud.getChatHud() == null) {
            System.out.println("[Nightly Logs] " + text);
            return;
        }

        Text message = Text.literal(    "").append(gradientText("DecideVisuals [Dev]", new Color(ColorUtil.fade(90)),new Color(ColorUtil.fade(180)))).append(Text.literal(" » ").formatted(Formatting.GRAY)).append(Text.literal(text).formatted(Formatting.WHITE));

        mc.inGameHud.getChatHud().addMessage(message);
    }



    public Text gradientText(String text, Color start, Color end) {
        Text result = Text.empty();
        int length = text.length();

        for (int i = 0; i < length; i++) {
            float ratio = i / (float) (length - 1);
            int r = (int) (start.getRed()   * (1 - ratio) + end.getRed()   * ratio);
            int g = (int) (start.getGreen() * (1 - ratio) + end.getGreen() * ratio);
            int b = (int) (start.getBlue()  * (1 - ratio) + end.getBlue()  * ratio);

            TextColor mcColor = TextColor.fromRgb((r << 16) | (g << 8) | b);

            Text charText = Text.literal(String.valueOf(text.charAt(i)))
                    .copy()
                    .styled(style -> style.withColor(mcColor));

            result = result.copy().append(charText);
        }
        return result;
    }

    public static Text solidText(String text, int color) {
        TextColor mcColor = TextColor.fromRgb(color);
        return Text.literal(text)
                .styled(style -> style.withColor(mcColor));
    }


}
