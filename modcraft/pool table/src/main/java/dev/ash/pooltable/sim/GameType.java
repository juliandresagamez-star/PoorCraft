package dev.ash.pooltable.sim;

import java.util.Locale;

public enum GameType {
   AMERICAN_8(2),
   ENGLISH_8(2),
   NINE(2),
   TEN(2),
   STRAIGHT(2),
   ONE_POCKET(2),
   BANK(2),
   CUTTHROAT(3);

   public final int players;

   private GameType(int players) {
      this.players = players;
   }

   public String key() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public GameType next() {
      return values()[(this.ordinal() + 1) % values().length];
   }

   public boolean points() {
      return this == STRAIGHT || this == ONE_POCKET || this == BANK;
   }

   public int target(int straightTarget) {
      return this == STRAIGHT ? straightTarget : 8;
   }

   public int balls() {
      return this == NINE ? 9 : (this == TEN ? 10 : 15);
   }

   public boolean eightBall() {
      return this == AMERICAN_8 || this == ENGLISH_8;
   }
}
