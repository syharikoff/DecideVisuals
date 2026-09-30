package ru.white.ui.lv.settings;

import ru.white.module.api.settings.impl.BindSetting;
import ru.white.ui.fonts.FontsLV;
import ru.white.utils.math.Keyboard;

public class LvBindSetting extends SettingWidget {
   private final BindSetting backend;
   private boolean listening;

   public LvBindSetting(BindSetting bindSetting) {
      this.backend = bindSetting;
   }

   @Override
   public String name() {
      return this.backend.getName();
   }

   public void setKey(int n) {
      this.backend.set(n);
   }

   @Override
   public float height() {
      return 16.0F;
   }

   @Override
   public void render(float f, float f2, float f3, float f4) {
      String string = this.listening ? "Press key..." : this.keyDisplay();
      float f5 = FontsLV.MONTSERRAT_MEDIUM.width(string, 6.0F) + 10.0F;
      float f6 = f + f3 - f5 - 4.0F;
      float f7 = f2 + 2.0F;
      LvRenderHelper.drawName(this.backend.getName(), f, f2, f6 - (f + 6.0F) - 4.0F, f4);
      LvRenderHelper.drawBtn(f6, f7, f5, 12.0F, string, f4);
   }

   @Override
   public boolean isVisible() {
      return this.backend.getVisible().get();
   }

   @Override
   public boolean click(float f, float f2, float f3, float f4, float f5) {
      String string = this.listening ? "Press key..." : this.keyDisplay();
      float f6 = FontsLV.MONTSERRAT_MEDIUM.width(string, 6.0F) + 10.0F;
      float f7 = f + f3 - f6 - 4.0F;
      float f8 = f2 + 2.0F;
      if (f4 >= f7 && f4 <= f7 + f6 && f5 >= f8 && f5 <= f8 + 12.0F) {
         this.listening = !this.listening;
         return true;
      } else {
         return false;
      }
   }

   public void setListening(boolean bl) {
      this.listening = bl;
   }

   public boolean isListening() {
      return this.listening;
   }

   @Override
   public float preferredWidth() {
      String string = this.keyDisplay();
      return 6.0F + FontsLV.MONTSERRAT_MEDIUM.width(this.backend.getName(), 6.5F) + 6.0F + FontsLV.MONTSERRAT_MEDIUM.width(string, 6.0F) + 10.0F + 8.0F;
   }

   private String keyDisplay() {
      int n = this.backend.get();
      return n <= 0 ? "None" : Keyboard.keyName(n);
   }
}