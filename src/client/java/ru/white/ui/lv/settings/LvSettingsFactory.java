package ru.white.ui.lv.settings;

import java.util.ArrayList;
import java.util.List;
import ru.white.module.api.Module;
import ru.white.module.api.settings.Setting;
import ru.white.module.api.settings.impl.BindSetting;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.BooleanSettingHud;
import ru.white.module.api.settings.impl.ButtonSetting;
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.module.api.settings.impl.DelimiterSetting;
import ru.white.module.api.settings.impl.DragSetting;
import ru.white.module.api.settings.impl.ListSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.ModeSettingHud;
import ru.white.module.api.settings.impl.MultiBooleanSetting;
import ru.white.module.api.settings.impl.PixelGridSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.module.api.settings.impl.StringSetting;
import ru.white.ui.fonts.FontsLV;
import ru.white.ui.theme.ThemeManager;
import ru.white.utils.render.Draw;

public final class LvSettingsFactory {
   private LvSettingsFactory() {
   }

   public static SettingWidget create(Setting<?> setting) {
      if (setting instanceof SliderSetting sliderSetting) {
         return new LvSliderSetting(sliderSetting);
      } else if (setting instanceof BooleanSetting booleanSetting) {
         return new LvBoolSetting(booleanSetting);
      } else if (setting instanceof ModeSetting modeSetting) {
         return new LvSelectSetting(modeSetting);
      } else if (setting instanceof MultiBooleanSetting multiBooleanSetting) {
         return new LvMultiSelectSetting(multiBooleanSetting);
      } else if (setting instanceof ColorSetting colorSetting) {
         return new LvColorSetting(colorSetting);
      } else if (setting instanceof BindSetting bindSetting) {
         return new LvBindSetting(bindSetting);
      } else if (setting instanceof StringSetting stringSetting) {
         return new LvTextSetting(stringSetting);
      } else if (setting instanceof DelimiterSetting delimiterSetting) {
         return new LvSeparatorSetting(delimiterSetting);
      } else if (setting instanceof ButtonSetting buttonSetting) {
         return new LvButtonRowSetting(buttonSetting);
      } else if (setting instanceof DragSetting dragSetting) {
         return new LvDragSetting(dragSetting);
      } else if (setting instanceof PixelGridSetting pixelGridSetting) {
         return new LvPixelGridSetting(pixelGridSetting);
      } else if (setting instanceof BooleanSettingHud booleanSettingHud) {
         return new LvBoolHudRow(booleanSettingHud);
      } else if (setting instanceof ModeSettingHud modeSettingHud) {
         return new LvSelectHudRow(modeSettingHud);
      } else if (setting instanceof ListSetting<?> listSetting) {
         return new LvListRowSetting(listSetting);
      } else {
         return null;
      }
   }

   public static List<SettingWidget> build(Module module) {
      LvTextSetting.unfocusAll();
      List<SettingWidget> list = new ArrayList<>();
      for (Setting<?> setting : module.getSettings()) {
         SettingWidget settingWidget = create(setting);
         if (settingWidget != null) {
            list.add(settingWidget);
         }
      }
      return list;
   }

   private static final class LvListRowSetting extends SettingWidget {
      private final ListSetting<?> backend;

      private LvListRowSetting(ListSetting<?> listSetting) {
         this.backend = listSetting;
      }

      @Override
      public String name() {
         return this.backend.getName();
      }

      @Override
      public float height() {
         return 16.0F;
      }

      @Override
      public void render(float f, float f2, float f3, float f4) {
         String string = String.valueOf(this.backend.getValue());
         float f5 = FontsLV.MONTSERRAT_MEDIUM.width(string, 6.0F) + 10.0F;
         float f6 = f + f3 - f5 - 4.0F;
         float f7 = f2 + 2.0F;
         LvRenderHelper.drawName(this.backend.getName(), f, f2, f6 - (f + 6.0F) - 4.0F, f4);
         Draw.rect(f6, f7, f5, 12.0F, ThemeManager.accentSoft(50.0F * f4), 3.0F);
         FontsLV.MONTSERRAT_MEDIUM.draw(string, f6 + 5.0F, f7 + 3.25F, 6.0F, ThemeManager.accentSoft(220.0F * f4));
      }

      @Override
      public boolean isVisible() {
         return this.backend.getVisible().get();
      }

      @Override
      public boolean click(float f, float f2, float f3, float f4, float f5) {
         return false;
      }

      @Override
      public float preferredWidth() {
         return 6.0F + FontsLV.MONTSERRAT_MEDIUM.width(this.backend.getName(), 6.5F) + 6.0F + FontsLV.MONTSERRAT_MEDIUM.width(String.valueOf(this.backend.getValue()), 6.0F) + 10.0F + 8.0F;
      }
   }

   private static final class LvBoolHudRow extends SettingWidget {
      private final BooleanSettingHud backend;

      private LvBoolHudRow(BooleanSettingHud booleanSettingHud) {
         this.backend = booleanSettingHud;
      }

      @Override
      public String name() {
         return this.backend.getName();
      }

      @Override
      public float height() {
         return 16.0F;
      }

      @Override
      public void render(float f, float f2, float f3, float f4) {
         String string = this.backend.getValue() ? "Вкл" : "Выкл";
         float f5 = FontsLV.MONTSERRAT_MEDIUM.width(string, 6.0F) + 10.0F;
         float f6 = f + f3 - f5 - 4.0F;
         float f7 = f2 + 2.0F;
         LvRenderHelper.drawName(this.backend.getName(), f, f2, f6 - (f + 6.0F) - 4.0F, f4);
         Draw.rect(f6, f7, f5, 12.0F, this.backend.getValue() ? ThemeManager.accentSoft(160.0F * f4) : ThemeManager.accentSoft(50.0F * f4), 3.0F);
         FontsLV.MONTSERRAT_MEDIUM.draw(string, f6 + 5.0F, f7 + 3.25F, 6.0F, ThemeManager.accentSoft(220.0F * f4));
      }

      @Override
      public boolean isVisible() {
         return this.backend.getVisible().get();
      }

      @Override
      public boolean click(float f, float f2, float f3, float f4, float f5) {
         this.backend.set(!this.backend.getValue());
         return true;
      }

      @Override
      public float preferredWidth() {
         return 6.0F + FontsLV.MONTSERRAT_MEDIUM.width(this.backend.getName(), 6.5F) + 6.0F + 32.0F + 8.0F;
      }
   }

   private static final class LvSelectHudRow extends SettingWidget {
      private final ModeSettingHud backend;

      private LvSelectHudRow(ModeSettingHud modeSettingHud) {
         this.backend = modeSettingHud;
      }

      @Override
      public String name() {
         return this.backend.getName();
      }

      @Override
      public float height() {
         return 16.0F;
      }

      @Override
      public void render(float f, float f2, float f3, float f4) {
         String string = String.valueOf(this.backend.getValue());
         float f5 = FontsLV.MONTSERRAT_MEDIUM.width(string, 6.0F) + 10.0F;
         float f6 = f + f3 - f5 - 4.0F;
         float f7 = f2 + 2.0F;
         LvRenderHelper.drawName(this.backend.getName(), f, f2, f6 - (f + 6.0F) - 4.0F, f4);
         Draw.rect(f6, f7, f5, 12.0F, ThemeManager.accentSoft(50.0F * f4), 3.0F);
         FontsLV.MONTSERRAT_MEDIUM.draw(string, f6 + 5.0F, f7 + 3.25F, 6.0F, ThemeManager.accentSoft(220.0F * f4));
      }

      @Override
      public boolean isVisible() {
         return this.backend.getVisible().get();
      }

      @Override
      public boolean click(float f, float f2, float f3, float f4, float f5) {
         int index = (this.backend.getIndex() + 1) % this.backend.values.size();
         this.backend.set(this.backend.values.get(index));
         return true;
      }

      @Override
      public float preferredWidth() {
         return 6.0F + FontsLV.MONTSERRAT_MEDIUM.width(this.backend.getName(), 6.5F) + 6.0F + FontsLV.MONTSERRAT_MEDIUM.width(String.valueOf(this.backend.getValue()), 6.0F) + 10.0F + 8.0F;
      }
   }
}