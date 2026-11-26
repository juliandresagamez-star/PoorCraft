package dev.ash.pooltable.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.renderer.texture.OverlayTexture;

final class CubeMesh {
   private static final float[][] FACES = new float[][]{
      {0.0F, 1.0F, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F},
      {0.0F, -1.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F},
      {0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F},
      {0.0F, 0.0F, -1.0F, -1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F},
      {1.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.0F, 0.0F, 1.0F, 0.0F},
      {-1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F}
   };
   private static final int[] TILE = new int[]{0, 2, 1, 1, 1, 1};

   private CubeMesh() {
   }

   static void emit(VertexConsumer vc, Pose pose, int ball, int light, int argb) {
      float v0 = ball / 16.0F;
      float v1 = (ball + 1) / 16.0F;

      for (int f = 0; f < 6; f++) {
         float[] d = FACES[f];
         float u0 = TILE[f] / 3.0F;
         float u1 = (TILE[f] + 1) / 3.0F;
         corner(vc, pose, d, -1, 1, u0, v0, light, argb);
         corner(vc, pose, d, -1, -1, u0, v1, light, argb);
         corner(vc, pose, d, 1, -1, u1, v1, light, argb);
         corner(vc, pose, d, 1, 1, u1, v0, light, argb);
      }
   }

   private static void corner(VertexConsumer vc, Pose pose, float[] d, int right, int up, float u, float v, int light, int argb) {
      float x = d[0] + right * d[3] + up * d[6];
      float y = d[1] + right * d[4] + up * d[7];
      float z = d[2] + right * d[5] + up * d[8];
      vc.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, d[0], d[1], d[2]);
   }
}
