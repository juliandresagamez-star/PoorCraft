package dev.ash.pooltable.client.compat.v26_1;

import dev.ash.pooltable.client.compat.GuiShim;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public class GuiShim26_1 implements GuiShim {
   @Override
   public void blit(GuiGraphicsExtractor g, Identifier texture, int x, int y, float u, float v, int w, int h, int texW, int texH) {
      g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, texW, texH);
   }
}
