package ru.decide.screen;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import ru.decide.alt.Account;
import ru.decide.alt.AltManager;
import ru.decide.theme.ThemeColor;
import ru.decide.utils.animation.Animation;
import ru.decide.utils.animation.Easings;
import ru.decide.utils.annotation.IMinecraft;
import ru.decide.utils.colors.ColorUtil;
import ru.decide.utils.math.MathUtil;
import ru.decide.utils.render.Render2D;
import ru.decide.utils.render.RenderUtil;
import ru.decide.utils.render.Scissor;
import ru.decide.utils.render.ScreenBlur;
import ru.decide.utils.render.font.Font;
import ru.decide.utils.render.font.Fonts;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AltManagerScreen extends Screen implements IMinecraft {

    private final Screen parent;
    private final Animation alpha = new Animation();
    private float scaleFix = 1F;
    private float mouseX, mouseY;

    private String inputText = "";
    private boolean inputFocused = false;
    private float scroll = 0, scrollTarget = 0;


    private boolean settingsOpen = false;
    private String randomBase = "";
    private boolean baseFocused = false;

    private final Animation addAnim = new Animation();
    private final Animation genAnim = new Animation();
    private final Animation inputAnim = new Animation();
    private final Animation gearAnim = new Animation();
    private final Animation settingsAnim = new Animation();
    private final Animation baseAnim = new Animation();

    private float gearX, gearY, gearW, gearH;
    private float sideX, sideY, sideW, sideH;
    private float baseX, baseY, baseW, baseH;
    private final java.util.Map<Account, RowAnims> rowAnims = new java.util.IdentityHashMap<>();
    private final java.util.Set<Account> removing = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private static final class RowAnims {
        final Animation hover = new Animation();
        final Animation del = new Animation();
        final Animation active = new Animation();
        final Animation appear = new Animation();
        final Animation headFade = new Animation();
        final Animation fav = new Animation();
        final Animation favHover = new Animation();
        float curOff;
        boolean hasOff;
        RowAnims() { appear.set(0); headFade.set(0); }
    }

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");


    private float panelX, panelY, panelW, panelH;
    private float inputX, inputY, inputW, inputH;
    private float addX, addY, addW, addH;
    private float genX, genY, genW, genH;
    private float listX, listY, listW, listH;
    private final List<float[]> rowRects = new ArrayList<>();
    private final List<float[]> delRects = new ArrayList<>();
    private final List<float[]> favRects = new ArrayList<>();
    private final List<Account> rowRefs = new ArrayList<>();

    private static final float ROW_H = 20;
    private static final float ROW_GAP = 4F;

    public AltManagerScreen(Screen parent) {
        super(Text.literal("AltManager"));
        this.parent = parent;
    }

    @Override
    
    protected void init() {
        alpha.set(0);
        alpha.run(1, 0.5F, Easings.BACK_OUT);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.mouseX = mouseX / scaleFix;
        this.mouseY = mouseY / scaleFix;
        alpha.update();
        settingsAnim.update();
        float targetScale = 2F;
        float currentScale = (float) mc.getWindow().getScaleFactor();
        scaleFix = targetScale / currentScale;

        int screenWidth = (int) (mc.getWindow().getScaledWidth() / scaleFix);
        int screenHeight = (int) (mc.getWindow().getScaledHeight() / scaleFix);

        if (context != null) context.getMatrices().pushMatrix();
        Render2D.beginOverlay();

        float a = alpha.get();




        float parallaxStrength = 0.07F; // Сила смещения
        float offsetX = (screenWidth / 2F - (float) mouseX) * parallaxStrength;
        float offsetY = (screenHeight / 2F - (float) mouseY) * parallaxStrength;


        float bgScale = 1.1F;
        float bgW = screenWidth * bgScale;
        float bgH = screenHeight * bgScale;
        float bgX = (screenWidth - bgW) / 2F + offsetX;
        float bgY = (screenHeight - bgH) / 2F + offsetY;

        RenderUtil.Images.texture(Identifier.of("decide","textures/frame/menu.png"), bgX, bgY, bgW, bgH, ColorUtil.getColor(255, a));




        ScreenBlur.capture(2);

        RenderUtil.Blur.blur(0,0,screenWidth,screenHeight,a,ColorUtil.getColor(0,a * 0.2F));

        ScreenBlur.capture(4);

        Font font = Fonts.sf_regular;

        panelW = 280;
        panelH = 274;
        panelX = screenWidth / 2F - panelW / 2F;
        panelY = screenHeight / 2F - panelH / 2F + 16 - 16 * a;

        float pad = 5;
        float btnH = 20;
        float gap = 5;


        addY = genY = panelY + panelH - pad - btnH;
        addH = genH = btnH;
        addW = genW = (panelW - pad * 2 - gap) / 2F;
        addX = panelX + pad;
        genX = addX + addW + gap;


        inputH = 20;
        gearW = gearH = inputH;
        inputX = panelX + pad;
        inputW = panelW - pad * 2 - gearW - 6;
        inputY = addY - gap - inputH;
        gearX = inputX + inputW + 6;
        gearY = inputY;


        listX = panelX + pad;
        listY = panelY + pad;
        listW = panelW - pad * 2;
        listH = inputY - gap - listY;
        renderSidePanel(a, font);


        RenderUtil.Blur.blur(panelX, panelY, panelW, panelH, a, 5, ColorUtil.getColor(20, a * 0.4F));
        //   RenderUtil.Render2D.outline(panelX, panelY, panelW, panelH, 0.75F, ColorUtil.getColor(255, a * 0.1F), 6);



        rowRects.clear(); delRects.clear(); favRects.clear(); rowRefs.clear();

        List<Account> all = AltManager.get().getAccounts();
        List<Account> accs = AltManager.get().getSorted();
        float contentH = accs.size() * (ROW_H + ROW_GAP);
        float maxScroll = Math.max(0, contentH - listH);
        scrollTarget = MathUtil.clamp(scrollTarget, 0, maxScroll);
        scroll += (scrollTarget - scroll) * 0.4F;


        rowAnims.keySet().retainAll(all);

        Scissor.enable(listX, listY, listW, listH, 2);
        float off = 0;
        String current = AltManager.get().currentName();
        java.util.List<Account> finished = null;
        for (Account acc : accs) {
            RowAnims an = rowAnims.computeIfAbsent(acc, k -> new RowAnims());
            boolean leaving = removing.contains(acc);

            an.appear.run(leaving ? 0 : 1, leaving ? 0.22 : 0.35, Easings.QUAD_OUT, true);

            float ap = MathUtil.clamp(an.appear.get(), 0F, 1F);
            float slotH = (ROW_H + ROW_GAP) * ap;

            if (!an.hasOff) { an.curOff = off; an.hasOff = true; }
            else an.curOff += (off - an.curOff) * 0.35F;
            float ry = listY - scroll + an.curOff;


            if (RenderUtil.Images.isUrlLoaded(acc.headUrl())) an.headFade.run(1, 0.3, Easings.QUAD_OUT, true);

            boolean visible = ry + slotH >= listY && ry <= listY + listH;
            boolean inside = mouseY() >= listY && mouseY() <= listY + listH;
            float dw = 16, dh = 16, dx = listX + listW - dw - 4, dy = ry + (ROW_H - dh) / 2F;
            float fx = dx - dw - 4, fy = dy;
            boolean hovered = !leaving && visible && inside && MathUtil.isHovered(mouseX(), mouseY(), listX, ry, listW, ROW_H);
            boolean delHover = !leaving && visible && inside && MathUtil.isHovered(mouseX(), mouseY(), dx, dy, dw, dh);
            boolean favHover = !leaving && visible && inside && MathUtil.isHovered(mouseX(), mouseY(), fx, fy, dw, dh);
            boolean active = acc.name.equalsIgnoreCase(current);

            an.hover.run(hovered && !delHover && !favHover ? 1 : 0, 0.18, Easings.QUAD_OUT, true);
            an.del.run(delHover ? 1 : 0, 0.18, Easings.QUAD_OUT, true);
            an.favHover.run(favHover ? 1 : 0, 0.18, Easings.QUAD_OUT, true);
            an.fav.run(acc.favorite ? 1 : 0, 0.25, Easings.QUAD_OUT, true);
            an.active.run(active ? 1 : 0, 0.25, Easings.QUAD_OUT, true);
            an.hover.update(); an.del.update(); an.favHover.update(); an.fav.update(); an.active.update(); an.appear.update(); an.headFade.update();

            float hp = MathUtil.clamp(an.hover.get(), 0F, 1F);
            float dp = MathUtil.clamp(an.del.get(), 0F, 1F);
            float fhp = MathUtil.clamp(an.favHover.get(), 0F, 1F);
            float fv = MathUtil.clamp(an.fav.get(), 0F, 1F);
            float acp = MathUtil.clamp(an.active.get(), 0F, 1F);
            float hf = MathUtil.clamp(an.headFade.get(), 0F, 1F);

            if (visible) {
                float ra = a * ap;
                float slide = (1F - ap) * 10F;
                float rx = listX + slide;
                float rw = listW;
                dx = rx + rw - dw - 3;

                int rowBg = ColorUtil.getColor(255, ra * (0.015F + 0.05F * hp + 0.03F * acp));
                RenderUtil.Render2D.rect(rx, ry, rw, ROW_H, rowBg, 4 );
           //   if (acp > 0.001F)
           //       RenderUtil.Render2D.outline(rx, ry, rw, ROW_H, 0.75F,
           //               ColorUtil.replAlpha(new Color(0x8E8ED6).getRGB(), ra * 0.7F * acp), 7);

                float asz = 12, axx = rx + 5, ay = ry + (ROW_H - asz) / 2F;
                RenderUtil.Render2D.rect(axx, ay, asz, asz, ColorUtil.getColor(255, ra * 0.06F), 3);
                RenderUtil.Images.url(acc.headUrl(), axx, ay, asz, asz, 1f, 3, ColorUtil.getColor(255, ra * hf));

                float tx = axx + asz + 4;
                font.draw(acc.name, tx, ry + 5, 8,
                        ColorUtil.getColor(255,ra * (0.7F + 0.3F * acp)));
                Fonts.sf_regular.draw(dateFormat.format(new Date(acc.created)), tx + 6
                                + font.getWidth(acc.name,8) , ry + 5, 5
                        ,ColorUtil.getColor(255,ra * (0.15F + 0.15F * acp)));

                dy = ry + (ROW_H - dh) / 2F;
                fx = dx - dw - 2; fy = dy;

                RenderUtil.Render2D.rect(fx, fy, dw, dh, ColorUtil.getColor(255, ra * (0.015F + 0.025F * fhp)), 3);
                int starCol = ColorUtil.overCol(ColorUtil.getColor(255, 255, 255, ra * (0.45F + 0.25F * fhp)),
                        ColorUtil.replAlpha(ColorUtil.getClientColor(1), ra), fv);

                Fonts.icon.drawCentered("o",fx + dw / 2F, fy + dh / 2F - 2.7F,6,starCol);


                RenderUtil.Render2D.rect(dx, dy, dw, dh, ColorUtil.getColor(255, ra * (0.015F + 0.025F * dp)), 3);
                Fonts.icon.drawCentered("W", dx + dw / 2F + 0.15F, dy + 4.6F, 7, ColorUtil.overCol(ColorUtil.getColor(255, 255, 255, ra * 0.5F),ColorUtil.getColor(255, 125, 125, ra * 0.9F),dp));

                if (!leaving) {
                    rowRects.add(new float[]{rx, ry, rw - dw * 2 - 16, ROW_H});
                    delRects.add(new float[]{dx, dy, dw, dh});
                    favRects.add(new float[]{fx, fy, dw, dh});
                    rowRefs.add(acc);
                }
            }


            if (leaving && ap <= 0.01F) {
                if (finished == null) finished = new java.util.ArrayList<>();
                finished.add(acc);
            }

            off += slotH;
        }
        if (finished != null) {
            for (Account acc : finished) {
                AltManager.get().remove(acc);
                rowAnims.remove(acc);
                removing.remove(acc);
            }
        }
        if (accs.isEmpty()) {
            font.drawCentered(ru.decide.lang.Lang.pick("Пока нет аккаунтов", "Пока нет аккаунтов"), listX + listW / 2F, listY + listH / 2F - 4, 12, ColorUtil.getColor(255,a * 0.15F));
        }
        Scissor.disable();


        inputAnim.run(inputFocused ? 1 : 0, 0.2, Easings.QUAD_OUT, true);
        inputAnim.update();
        float fp = MathUtil.clamp(inputAnim.get(), 0F, 1F);

        RenderUtil.Render2D.rect(inputX, inputY, inputW, inputH, ColorUtil.getColor(255, a * (0.015F + 0.03F * fp)), 4);
      //  RenderUtil.Render2D.outline(inputX, inputY, inputW, inputH, 0.5F,
      //          ColorUtil.replAlpha(ColorUtil.interpolateColor(ColorUtil.getColor(255, 255, 255, 1F), ColorUtil.getClientColor(1), fp),
      //                  a * (0.01F + 0.1F * fp)), 4);
        boolean blink = inputFocused && (System.currentTimeMillis() / 450L) % 2L == 0L;
        String shown = inputText.isEmpty() && !inputFocused ? "Введите ник" : inputText + (blink ? "_" : "");
        font.draw(shown, inputX + 10, inputY + 5.4F, 7,
                inputText.isEmpty() && !inputFocused ? ColorUtil.getColor(140,a) :ColorUtil.getColor(185,a));



        gearAnim.run(MathUtil.isHovered(mouseX(), mouseY(), gearX, gearY, gearW, gearH) || settingsOpen ? 1 : 0, 0.18, Easings.QUAD_OUT, true);
        gearAnim.update();
        float gp = MathUtil.clamp(gearAnim.get(), 0F, 1F);
        RenderUtil.Render2D.rect(gearX, gearY, gearW, gearH, ColorUtil.getColor(255, a * (0.015F + 0.04F * gp)), 4);
     //   RenderUtil.Render2D.outline(gearX, gearY, gearW, gearH, 0.5F,
     //           ColorUtil.replAlpha(ColorUtil.getColor(255, 255, 255, 1F), a * (0.015F + 0.12F * gp)), 4);


        Fonts.icon.draw("l",gearX + 6.25F, gearY + 6.3F, 8,ColorUtil.getColor(255,a * (0.35F + 0.3F * gp)));

        addAnim.run(MathUtil.isHovered(mouseX(), mouseY(), addX, addY, addW, addH) ? 1 : 0, 0.18, Easings.QUAD_OUT, true);
        genAnim.run(MathUtil.isHovered(mouseX(), mouseY(), genX, genY, genW, genH) ? 1 : 0, 0.18, Easings.QUAD_OUT, true);
        addAnim.update(); genAnim.update();
        drawButton(addX, addY, addW, addH, ru.decide.lang.Lang.pick("Добавить аккаунт", "Добавить аккаунт"), a, MathUtil.clamp(addAnim.get(), 0F, 1F), false);
        drawButton(genX, genY, genW, genH, ru.decide.lang.Lang.pick("Случайный аккаунт", "Случайный аккаунт"), a, MathUtil.clamp(genAnim.get(), 0F, 1F), false);


        Render2D.endOverlay();
        if (context != null) context.getMatrices().popMatrix();
    }

    private void drawButton(float x, float y, float w, float h, String label, float a, float hp, boolean accent) {
        int bg = accent
                ? ColorUtil.replAlpha(ColorUtil.getClientColor(1), a * (0.18F + 0.12F * hp))
                : ColorUtil.getColor(255, a * (0.015F + 0.03F * hp));
        RenderUtil.Render2D.rect(x, y, w, h, bg, 4);
        int border = ColorUtil.getColor(255, 255, 255, 1F);
       // RenderUtil.Render2D.outline(x, y, w, h, 0.5F, ColorUtil.replAlpha(border, a * (0.015F + 0.1F * hp)), 4);
        Fonts.sf_regular.drawCentered(label, x + w / 2F, y + h / 2F - 4.75F, 7
                , ColorUtil.getColor(175,a));
    }



    private void renderSidePanel(float a, Font font) {

        settingsAnim.run(settingsOpen ? 1 : 0, 0.25F, Easings.SINE_OUT);
        float sp = settingsAnim.get();

        float sa = a * sp;
        sideW = 168; sideH = 96;
        sideY = inputY + inputH - sideH;
        float hiddenX = panelX + panelW - sideW + 24;
        float shownX = panelX + panelW + 8;
        sideX = hiddenX + (shownX - hiddenX) - 8 + 8 * sa  ;

        RenderUtil.Blur.blur(sideX, sideY, sideW, sideH, sa, 6,
                ColorUtil.getColor(20, sa * 0.3F));

        float pad = 12;
        font.draw(ru.decide.lang.Lang.pick("Настройки рандома", "Random settings"), sideX + pad, sideY + 9, 8, ColorUtil.getColor(185, sa));
        font.draw(ru.decide.lang.Lang.pick("Начальный ник", "Initial nickname"), sideX + pad, sideY + 26, 6.5F, ColorUtil.getColor(185, sa));


        baseX = sideX + pad   ; baseY = sideY + 37; baseW = sideW - pad * 2; baseH = 20;
        baseAnim.run(baseFocused ? 1 : 0, 0.2, Easings.QUAD_OUT, true);
        baseAnim.update();
        float bf = MathUtil.clamp(baseAnim.get(), 0F, 1F);
        RenderUtil.Render2D.rect(baseX, baseY, baseW, baseH, ColorUtil.getColor(255,
                sa * (0.015F + 0.02F * bf)), 5);

        boolean blink = baseFocused && (System.currentTimeMillis() / 450L) % 2L == 0L;
        String shown = randomBase.isEmpty() && !baseFocused ? ru.decide.lang.Lang.pick("напр. night", "e.g. night") : randomBase + (blink ? "_" : "");
        font.draw(shown, baseX + 8, baseY + 6F, 6.5F,
                randomBase.isEmpty() && !baseFocused ? ColorUtil.getColor(140, sa) : ColorUtil.getColor(185, sa));

        font.draw(ru.decide.lang.Lang.pick("+ 4-12 случайных символов", "+ 4-12 random chars"), sideX + pad, sideY + sideH - 16, 7, ColorUtil.getColor(185, sa * 0.5F));
    }

    private float mouseX() { return mouseX; }
    private float mouseY() { return mouseY; }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        float mx = (float) (click.x() / scaleFix);
        float my = (float) (click.y() / scaleFix);
        if (click.button() != 0) return super.mouseClicked(click, doubled);

        boolean inSide = settingsOpen && MathUtil.isHovered(mx, my, sideX, sideY, sideW, sideH);


        if (inSide) {
            if (MathUtil.isHovered(mx, my, baseX, baseY, baseW, baseH)) {
                baseFocused = true; inputFocused = false;
            } else {
                baseFocused = false;
            }
            return true;
        }

        if (MathUtil.isHovered(mx, my, gearX, gearY, gearW, gearH)) {
            settingsOpen = !settingsOpen;
            baseFocused = settingsOpen;
            inputFocused = false;
            return true;
        }

        if (MathUtil.isHovered(mx, my, inputX, inputY, inputW, inputH)) {
            inputFocused = true; baseFocused = false;
            return true;
        } else {
            inputFocused = false;
        }

        if (MathUtil.isHovered(mx, my, addX, addY, addW, addH)) {
            tryAdd();
            return true;
        }
        if (MathUtil.isHovered(mx, my, genX, genY, genW, genH)) {
            inputText = AltManager.get().randomName(randomBase);
            inputFocused = true; baseFocused = false;
            return true;
        }


        for (int i = 0; i < favRects.size(); i++) {
            float[] r = favRects.get(i);
            if (MathUtil.isHovered(mx, my, r[0], r[1], r[2], r[3])) {
                AltManager.get().toggleFavorite(rowRefs.get(i));
                return true;
            }
        }

        for (int i = 0; i < delRects.size(); i++) {
            float[] r = delRects.get(i);
            if (MathUtil.isHovered(mx, my, r[0], r[1], r[2], r[3])) {
                removing.add(rowRefs.get(i));
                return true;
            }
        }

        for (int i = 0; i < rowRects.size(); i++) {
            float[] r = rowRects.get(i);
            if (MathUtil.isHovered(mx, my, r[0], r[1], r[2], r[3])) {
                AltManager.get().login(rowRefs.get(i));
                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    private void tryAdd() {
        Account added = AltManager.get().add(inputText);
        if (added != null) {
            inputText = "";
            scrollTarget = Float.MAX_VALUE;
        }
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int key = keyInput.key();
        if (baseFocused) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
                baseFocused = false;
            } else if (ru.decide.utils.other.ClipboardPaste.pasteCombo(key)) {
                randomBase = ru.decide.utils.other.ClipboardPaste.append(randomBase, 24);
            } else if (key == GLFW.GLFW_KEY_BACKSPACE && !randomBase.isEmpty()) {
                randomBase = randomBase.substring(0, randomBase.length() - 1);
            }
            return true;
        }
        if (inputFocused) {
            if (key == GLFW.GLFW_KEY_ENTER) {
                tryAdd();
            } else if (key == GLFW.GLFW_KEY_ESCAPE) {
                inputFocused = false;
            } else if (ru.decide.utils.other.ClipboardPaste.pasteCombo(key)) {
                inputText = ru.decide.utils.other.ClipboardPaste.append(inputText, 64);
            } else if (key == GLFW.GLFW_KEY_BACKSPACE && !inputText.isEmpty()) {
                inputText = inputText.substring(0, inputText.length() - 1);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (settingsOpen) { settingsOpen = false; return true; }
            mc.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (baseFocused && input.isValidChar() && randomBase.length() < 12) {
            String s = input.asString();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (Character.isLetterOrDigit(c) || c == '_') randomBase += c;
            }
            return true;
        }
        if (inputFocused && input.isValidChar() && inputText.length() < 16) {
            String s = input.asString();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (Character.isLetterOrDigit(c) || c == '_') inputText += c;
            }
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        scrollTarget -= (float) vertical * 22F;
        return true;
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}
