package dev.ash.pooltable.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import dev.ash.pooltable.PoolTable;
import dev.ash.pooltable.block.LampKind;
import dev.ash.pooltable.block.PoolLampBlock;
import dev.ash.pooltable.block.PoolLampBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class LampRenderer implements BlockEntityRenderer<PoolLampBlockEntity, LampRenderer.State> {
   public LampRenderer(Context context) {
   }

   public LampRenderer.State createRenderState() {
      return new LampRenderer.State();
   }

   public void extractRenderState(PoolLampBlockEntity lamp, LampRenderer.State state, float partialTick, Vec3 camera, @Nullable CrumblingOverlay breakProgress) {
      BlockEntityRenderState.extractBase(lamp, state, breakProgress);
      state.kind = lamp.kind();
      state.facing = (Direction)lamp.getBlockState().getValue(PoolLampBlock.FACING);
      Level level = lamp.getLevel();
      int hang = lamp.hang();
      if (state.kind.hanger != null && hang != 0 && level != null) {
         state.light = new int[hang];
         BlockPos pos = lamp.getBlockPos();

         for (int k = 1; k <= hang; k++) {
            state.light[k - 1] = Light.at(level, pos.above(k));
         }
      } else {
         state.light = new int[0];
      }
   }

   public void submit(LampRenderer.State state, PoseStack ps, SubmitNodeCollector out, CameraRenderState camera) {
      LampKind kind = state.kind;
      if (kind != null && state.light.length != 0) {
         ps.pushPose();
         ps.rotateAround(Axis.YP.rotationDegrees(-state.facing.toYRot()), 0.5F, 0.0F, 0.5F);
         int[] light = state.light;
         out.submitCustomGeometry(ps, RenderTypes.entitySolid(PoolTable.id("textures/block/lamp/" + kind.hanger + ".png")), (pose, vc) -> {
            for (int k = 1; k <= light.length; k++) {
               for (int i = 0; i < kind.hangers.length; i += 3) {
                  float x = (float)kind.hangers[i] / 16.0F;
                  float z = (float)kind.hangers[i + 1] / 16.0F;
                  float h = (float)kind.hangers[i + 2] / 32.0F;
                  post(vc, pose, x - h, k, z - h, x + h, k + 1, z + h, light[k - 1]);
               }
            }
         });
         ps.popPose();
      }
   }

   private static void post(VertexConsumer vc, Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
      float u0 = 0.5F - (x1 - x0);
      float u1 = 0.5F + (x1 - x0);
      quad(vc, pose, x0, z1, x1, z1, y0, y1, u0, u1, light, 0.0F, 1.0F);
      quad(vc, pose, x1, z0, x0, z0, y0, y1, u0, u1, light, 0.0F, -1.0F);
      quad(vc, pose, x1, z1, x1, z0, y0, y1, u0, u1, light, 1.0F, 0.0F);
      quad(vc, pose, x0, z0, x0, z1, y0, y1, u0, u1, light, -1.0F, 0.0F);
   }

   private static void quad(
      VertexConsumer vc, Pose pose, float xa, float za, float xb, float zb, float y0, float y1, float u0, float u1, int light, float nx, float nz
   ) {
      vertex(vc, pose, xa, y1, za, u0, 0.0F, light, nx, nz);
      vertex(vc, pose, xa, y0, za, u0, 1.0F, light, nx, nz);
      vertex(vc, pose, xb, y0, zb, u1, 1.0F, light, nx, nz);
      vertex(vc, pose, xb, y1, zb, u1, 0.0F, light, nx, nz);
   }

   private static void vertex(VertexConsumer vc, Pose pose, float x, float y, float z, float u, float v, int light, float nx, float nz) {
      vc.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, 0.0F, nz);
   }

   public AABB getRenderBoundingBox(PoolLampBlockEntity lamp) {
      return new AABB(lamp.getBlockPos()).expandTowards(0.0, 25.0, 0.0).inflate(1.0, 0.0, 1.0);
   }

   public boolean shouldRenderOffScreen() {
      return true;
   }

   public static class State extends BlockEntityRenderState {
      LampKind kind;
      Direction facing = Direction.SOUTH;
      int[] light = new int[0];
   }
}
