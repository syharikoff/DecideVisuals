package ru.decide.ui.lv;

import ru.decide.module.api.Category;

public enum LvCategory {
   VISUALS("Visuals", 'p'),
   HUD("Display", 'j'),
   UTILITIES("Utilities", 'r'),
   EVENTS("Information", 'i'),
   THEMES("Themes", 'B');

   private final String displayName;
   private final char icon;

   private LvCategory(String displayName, char icon) {
      this.displayName = displayName;
      this.icon = icon;
   }

   public String getDisplayName() {
      return this.displayName;
   }

   public char getIcon() {
      return this.icon;
   }

   public static LvCategory getCategoryForModule(Category c) {
      if (c == null) return null;
      return switch (c) {
         case VISUALS -> VISUALS;
         case HUD -> HUD;
         case UTILITIES -> UTILITIES;
         default -> null;
      };
   }
}
