package dev.ash.pooltable.block;

import net.minecraft.util.StringRepresentable;

public enum TableWood implements StringRepresentable {
   MAHOGANY("mahogany", "pool_table"),
   OAK("oak", "oak_pool_table"),
   SPRUCE("spruce", "spruce_pool_table"),
   BIRCH("birch", "birch_pool_table"),
   DARK_OAK("dark_oak", "dark_oak_pool_table"),
   CHERRY("cherry", "cherry_pool_table");

   public final String id;
   public final String item;

   private TableWood(String id, String item) {
      this.id = id;
      this.item = item;
   }

   public String getSerializedName() {
      return this.id;
   }
}
