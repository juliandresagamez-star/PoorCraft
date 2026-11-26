package dev.ash.pooltable.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ash.pooltable.block.CueRackBlock;
import dev.ash.pooltable.block.CueRackBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class CueRackRenderer implements BlockEntityRenderer<CueRackBlockEntity, CueRackRenderer.State> {
   private static final double BUTT = 0.875;
   private final ItemModelResolver items;

   public CueRackRenderer(Context context) {
      this.items = context.itemModelResolver();
   }

   public CueRackRenderer.State createRenderState() {
      return new CueRackRenderer.State();
   }

   public void extractRenderState(
      CueRackBlockEntity rack, CueRackRenderer.State state, float partialTick, Vec3 camera, @Nullable CrumblingOverlay breakProgress
   ) {
      BlockEntityRenderState.extractBase(rack, state, breakProgress);
      state.cues.clear();
      state.at.clear();
      state.turn.clear();
      BlockState blockState = rack.getBlockState();
      if (blockState.getBlock() instanceof CueRackBlock block && rack.getLevel() != null && rack.count() != 0) {
         state.lit = LightCoordsUtil.max(state.lightCoords, Light.at(rack.getLevel(), rack.getBlockPos().above()));

         for (int i = 0; i < Math.min(block.size(), rack.size()); i++) {
            ItemStack cue = rack.get(i);
            if (!cue.isEmpty()) {
               ItemStackRenderState model = new ItemStackRenderState();
               this.items.updateForTopItem(model, cue, ItemDisplayContext.NONE, rack.getLevel(), null, (int)rack.getBlockPos().asLong() + i);
               state.cues.add(model);
               state.at.add(block.slotPos(blockState, i));
               state.turn.add((float)(i * 37 % 90));
            }
         }
      }
   }

   public void submit(CueRackRenderer.State state, PoseStack ps, SubmitNodeCollector out, CameraRenderState camera) {
      for (int i = 0; i < state.cues.size(); i++) {
         Vec3 at = state.at.get(i);
         ps.pushPose();
         ps.translate(at.x, at.y + 0.875, at.z);
         ps.last().rotate(Axis.YP.rotationDegrees(state.turn.get(i)));
         state.cues.get(i).submit(ps, out, state.lit, OverlayTexture.NO_OVERLAY, 0);
         ps.popPose();
      }
   }

   public AABB getRenderBoundingBox(CueRackBlockEntity rack) {
      BlockPos p = rack.getBlockPos();
      return new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 2, p.getZ() + 1);
   }

   public static class State extends BlockEntityRenderState {
      final List<ItemStackRenderState> cues = new ArrayList<>();
      final List<Vec3> at = new ArrayList<>();
      final List<Float> turn = new ArrayList<>();
      int lit;
   }
}
