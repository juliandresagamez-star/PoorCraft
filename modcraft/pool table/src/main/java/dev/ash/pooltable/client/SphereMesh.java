package dev.ash.pooltable.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.renderer.texture.OverlayTexture;

final class SphereMesh {
   private final int lat;
   private final int lon;
   private final float[] px;
   private final float[] py;
   private final float[] pz;
   private final float[] u;
   private final float[] v;
   static final SphereMesh HIGH = new SphereMesh(14, 24);
   static final SphereMesh LOW = new SphereMesh(8, 12);

   private SphereMesh(int lat, int lon) {
      this.lat = lat;
      this.lon = lon;
      int n = (lat + 1) * (lon + 1);
      this.px = new float[n];
      this.py = new float[n];
      this.pz = new float[n];
      this.u = new float[n];
      this.v = new float[n];

      for (int i = 0; i <= lat; i++) {
         double a = (Math.PI / 2) - Math.PI * i / lat;

         for (int j = 0; j <= lon; j++) {
            double b = (Math.PI * 2) * j / lon;
            int k = i * (lon + 1) + j;
            this.px[k] = (float)(Math.cos(a) * Math.sin(b));
            this.py[k] = (float)Math.sin(a);
            this.pz[k] = (float)(Math.cos(a) * Math.cos(b));
            this.u[k] = (float)j / lon;
            this.v[k] = (float)i / lat;
         }
      }
   }

   void emit(VertexConsumer vc, Pose pose, int ball, int light, int argb) {
      float u0 = ball % 4 / 4.0F;
      float v0 = ball / 4 / 4.0F;

      for (int i = 0; i < this.lat; i++) {
         for (int j = 0; j < this.lon; j++) {
            int a = i * (this.lon + 1) + j;
            int b = (i + 1) * (this.lon + 1) + j;
            this.vertex(vc, pose, a, u0, v0, light, argb);
            this.vertex(vc, pose, b, u0, v0, light, argb);
            this.vertex(vc, pose, b + 1, u0, v0, light, argb);
            this.vertex(vc, pose, a + 1, u0, v0, light, argb);
         }
      }
   }

   private void vertex(VertexConsumer vc, Pose pose, int k, float u0, float v0, int light, int argb) {
      vc.addVertex(pose, this.px[k], this.py[k], this.pz[k])
         .setColor(argb)
         .setUv(u0 + this.u[k] / 4.0F, v0 + this.v[k] / 4.0F)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(light)
         .setNormal(pose, this.px[k], this.py[k], this.pz[k]);
   }
}
