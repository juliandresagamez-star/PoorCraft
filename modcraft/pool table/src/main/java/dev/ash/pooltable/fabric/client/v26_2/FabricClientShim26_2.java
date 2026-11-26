package dev.ash.pooltable.fabric.client.v26_2;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ash.pooltable.fabric.client.FabricClientShim;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

public class FabricClientShim26_2 implements FabricClientShim {
   @Override
   public void drawOutline(LevelRenderContext context, AABB box) {
      Vec3 cam = context.levelState().cameraRenderState.pos;
      float width = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
      PoseStack ps = context.poseStack();
      ps.pushPose();
      ps.translate(-cam.x, -cam.y, -cam.z);
      context.submitNodeCollector().submitShapeOutline(ps, Shapes.create(box), RenderTypes.lines(), ARGB.black(102), width, false);
      ps.popPose();
   }
}
