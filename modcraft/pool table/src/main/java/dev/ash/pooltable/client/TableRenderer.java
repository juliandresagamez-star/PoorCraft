package dev.ash.pooltable.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import dev.ash.pooltable.PoolClientConfig;
import dev.ash.pooltable.PoolConfig;
import dev.ash.pooltable.PoolTable;
import dev.ash.pooltable.block.GameState;
import dev.ash.pooltable.block.PoolTableBlockEntity;
import dev.ash.pooltable.block.TableFrame;
import dev.ash.pooltable.block.TableVisuals;
import dev.ash.pooltable.client.compat.ClientShim;
import dev.ash.pooltable.sim.GameType;
import dev.ash.pooltable.sim.Guide;
import dev.ash.pooltable.sim.PoolSim;
import dev.ash.pooltable.sim.Rules;
import dev.ash.pooltable.sim.TableDims;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public class TableRenderer implements BlockEntityRenderer<PoolTableBlockEntity, TableRenderer.State> {
   private static final Identifier BALLS = PoolTable.id("textures/entity/balls.png");
   private static final Identifier CUBES = PoolTable.id("textures/entity/ball_cubes.png");
   private static final Identifier BALLS_ENGLISH = PoolTable.id("textures/entity/balls_english.png");
   private static final Identifier CUBES_ENGLISH = PoolTable.id("textures/entity/ball_cubes_english.png");
   private static final int CALL_RING = -788532048;
   private static final int[] SEAT_RING = new int[]{-1056978884, -1068447489};
   private static final Identifier CUE = PoolTable.id("textures/entity/cue.png");
   private static final Identifier WHITE = PoolTable.id("textures/entity/white.png");
   private static final Identifier SHADOW = Identifier.withDefaultNamespace("textures/misc/shadow.png");
   private static final double R = 0.075;
   private static final float BALL_Y = 0.8875F;
   private static final double H = 0.075 * Math.sqrt(0.5);
   private static final float CUBE_Y = (float)(0.8125 + H);
   private static final float LINE_Y = 0.8165F;
   private static final double CUE_LENGTH = 1.45;
   private static final double CUE_TIP_R = 0.011;
   private static final double CUE_BUTT_R = 0.026;
   private static final double CUE_ELEVATION = Math.toRadians(8.0);

   public TableRenderer(Context context) {
   }

   public TableRenderer.State createRenderState() {
      return new TableRenderer.State();
   }

   public void extractRenderState(PoolTableBlockEntity be, TableRenderer.State state, float partialTick, Vec3 camera, @Nullable CrumblingOverlay breakProgress) {
      BlockEntityRenderState.extractBase(be, state, breakProgress);
      state.be = be;
      state.partialTick = partialTick;
   }

   public void submit(TableRenderer.State state, PoseStack ps, SubmitNodeCollector out, CameraRenderState camera) {
      PoolTableBlockEntity be = state.be;
      float partialTick = state.partialTick;
      Level level = be == null ? null : be.getLevel();
      Minecraft mc = Minecraft.getInstance();
      if (level != null && mc.player != null) {
         double now = (float)level.getGameTime() + partialTick;
         TableFrame frame = be.frame();
         int light = Light.at(level, be.getBlockPos().above());
         boolean active = be == ClientAim.table();
         TableVisuals v = be.visuals;
         roll(be, partialTick, now);
         double camDist = ClientShim.get().mainCamera().position().distanceTo(frame.centre());
         SphereMesh mesh = camDist < 14.0 ? SphereMesh.HIGH : SphereMesh.LOW;
         ps.pushPose();
         ps.translate(0.5, 0.0, 0.5);
         ps.last().rotate(Axis.YP.rotationDegrees(-frame.facing().toYRot()));
         PoolSim sim = be.sim;
         boolean inHand = active && ClientAim.stage() == ClientAim.Stage.PLACE;
         int trayLight = Light.at(level, frame.part(0, 2).relative(frame.facing()));
         out.submitCustomGeometry(ps, RenderTypes.entitySolid(ballTexture(be)), (pose, balls) -> {
            PoseStack local = at(pose);

            for (int i = 0; i < 16; i++) {
               if (sim.on[i] && (!inHand || i != 0)) {
                  ball(local, balls, mesh, i, sim.pathX(i, partialTick), 0.8875F, sim.pathZ(i, partialTick), v.orient[i], light, -1);
               }
            }

            for (TableVisuals.Drop d : v.drops) {
               double s = (now - d.start()) / 8.0;
               if (!(s < 0.0) && !(s >= 1.0)) {
                  double[] p = TableDims.POCKETS;
                  double t = Math.min(1.0, s * 1.8);
                  double x = Mth.lerp(t, d.x(), p[d.pocket() * 4]);
                  double z = Mth.lerp(t, d.z(), p[d.pocket() * 4 + 1]);
                  ball(local, balls, mesh, d.ball(), x, 0.8875F - s * s * 0.3, z, v.orient[d.ball()], light, -1);
               }
            }

            int slot = 0;

            for (int b : be.tray) {
               if (!v.dropping(b, now)) {
                  double x = -1.185 + slot++ * 0.154;
                  Quaternionf q = new Quaternionf().rotateZ((b * 37 % 20 - 10) * 0.05F);
                  ball(local, balls, mesh, b, x, 0.605, 2.39625, q, trayLight, -1);
               }
            }
         });
         out.submitCustomGeometry(ps, RenderTypes.entityShadow(SHADOW), (pose, shadows) -> {
            for (int i = 0; i < 16; i++) {
               if (sim.on[i] && (!inHand || i != 0)) {
                  flat(pose, shadows, sim.pathX(i, partialTick) + 0.012, sim.pathZ(i, partialTick) + 0.012, shadow(), 0.004F, -1946157056, light);
               }
            }
         });
         cues(be, v, ps, out, light, now, active, mc);
         pocketRings(be, v, ps, out, now, active, mc);
         ps.popPose();
      }
   }

   private static PoseStack at(Pose pose) {
      PoseStack local = new PoseStack();
      local.last().set(pose);
      return local;
   }

   private static void roll(PoolTableBlockEntity be, float partialTick, double now) {
      TableVisuals v = be.visuals;
      if (now != v.lastFrame) {
         v.lastFrame = now;
         PoolSim sim = be.sim;

         for (int i = 0; i < 16; i++) {
            double x = sim.pathX(i, partialTick);
            double z = sim.pathZ(i, partialTick);
            if (v.shownValid && sim.on[i]) {
               double dx = x - v.shownX[i];
               double dz = z - v.shownZ[i];
               double d = Math.sqrt(dx * dx + dz * dz);
               if (d > 1.0E-7 && d < 0.6) {
                  Quaternionf q = new Quaternionf().rotateAxis((float)(d / 0.075), (float)(dz / d), 0.0F, (float)(-dx / d));
                  v.orient[i].premul(q).normalize();
               }
            }

            v.shownX[i] = x;
            v.shownZ[i] = z;
         }

         v.shownValid = true;
      }
   }

   private static Identifier ballTexture(PoolTableBlockEntity be) {
      boolean english = be.game.type == GameType.ENGLISH_8;
      return cubes() ? (english ? CUBES_ENGLISH : CUBES) : (english ? BALLS_ENGLISH : BALLS);
   }

   private static boolean cubes() {
      return PoolClientConfig.cubeBalls();
   }

   private static void ball(PoseStack ps, VertexConsumer vc, SphereMesh mesh, int ball, double x, double y, double z, Quaternionf orient, int light, int argb) {
      ps.pushPose();
      if (cubes()) {
         ps.translate(x, y - 0.075 + H, z);
         ps.scale((float)H, (float)H, (float)H);
         CubeMesh.emit(vc, ps.last(), ball, light, argb);
      } else {
         ps.translate(x, y, z);
         ps.last().rotate(orient);
         ps.scale(0.075F, 0.075F, 0.075F);
         mesh.emit(vc, ps.last(), ball, light, argb);
      }

      ps.popPose();
   }

   private static double edge(double dx, double dz) {
      return cubes() ? H / Math.max(Math.abs(dx), Math.abs(dz)) : 0.075;
   }

   private static double shadow() {
      return cubes() ? H * 1.4 : 0.09375;
   }

   private static void flat(Pose pose, VertexConsumer vc, double x, double z, double half, float lift, int argb, int light) {
      float y = (float)(0.8125 + lift);
      float x0 = (float)(x - half);
      float x1 = (float)(x + half);
      float z0 = (float)(z - half);
      float z1 = (float)(z + half);
      quadVertex(vc, pose, x0, y, z0, 0.0F, 0.0F, argb, light);
      quadVertex(vc, pose, x0, y, z1, 0.0F, 1.0F, argb, light);
      quadVertex(vc, pose, x1, y, z1, 1.0F, 1.0F, argb, light);
      quadVertex(vc, pose, x1, y, z0, 1.0F, 0.0F, argb, light);
   }

   private static void quadVertex(VertexConsumer vc, Pose pose, float x, float y, float z, float u, float v, int argb, int light) {
      vc.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
   }

   private static void cues(PoolTableBlockEntity be, TableVisuals v, PoseStack ps, SubmitNodeCollector out, int light, double now, boolean active, Minecraft mc) {
      PoolSim sim = be.sim;
      double sinceStrike = now - v.strikeTime;
      if (sinceStrike >= 0.0 && sinceStrike < 8.0) {
         double pull = sinceStrike < 1.2 ? v.strikePull * (1.0 - sinceStrike / 1.2) : -0.03;
         cue(ps, out, light, v.strikeX, v.strikeZ, (double)v.strikeAngle, pull, (double)v.strikeSpin);
      } else if (!be.moving() && sim.on[0]) {
         double cx = sim.x[0];
         double cz = sim.z[0];
         if (active && ClientAim.aiming()) {
            cue(ps, out, light, cx, cz, ClientAim.angle, 0.03 + ClientAim.power * 0.3, (double)ClientAim.spin);
            guide(be, ps, out);
         } else if (now - v.aimTime < 20.0 && v.aimPlayer != mc.player.getId()) {
            long nanos = System.nanoTime();
            float k = (float)(1.0 - Math.exp(-Mth.clamp((nanos - v.aimNanos) / 1.0E9, 0.0, 0.1) / 0.09));
            v.aimNanos = nanos;
            v.shownAimAngle = v.shownAimAngle + (float)Math.toRadians(Mth.wrapDegrees((float)Math.toDegrees(v.aimAngle - v.shownAimAngle))) * k;
            v.shownAimPower = v.shownAimPower + (v.aimPower - v.shownAimPower) * k;
            cue(ps, out, light, cx, cz, (double)v.shownAimAngle, 0.03 + v.shownAimPower * 0.3, 0.0);
         }

         if (active && ClientAim.view() == ClientAim.View.PLACE && ClientAim.ghostShown) {
            double gx = ClientAim.ghostX;
            double gz = ClientAim.ghostZ;
            out.submitCustomGeometry(
               ps, RenderTypes.entityShadow(SHADOW), (pose, vc) -> flat(pose, vc, gx + 0.012, gz + 0.012, shadow(), 0.004F, 1342177280, light)
            );
            int tint = ClientAim.ghostValid ? -1593835521 : -1593880496;
            out.submitCustomGeometry(
               ps,
               RenderTypes.entityTranslucent(ballTexture(be)),
               (pose, ghost) -> ball(at(pose), ghost, SphereMesh.HIGH, 0, gx, 0.8875F, gz, new Quaternionf(), 15728880, tint)
            );
         }
      }
   }

   private static void cue(PoseStack ps, SubmitNodeCollector out, int light, double bx, double bz, double angle, double pull, double spin) {
      out.submitCustomGeometry(ps, RenderTypes.entityCutout(CUE), (pose, vc) -> cue(pose, vc, light, bx, bz, angle, pull, spin));
   }

   private static void cue(Pose pose, VertexConsumer vc, int light, double bx, double bz, double angle, double pull, double spin) {
      double dx = Math.sin(angle);
      double dz = Math.cos(angle);
      double gap = edge(dx, dz) + 0.012 + pull;
      double y = cubes() ? CUBE_Y + spin * 0.4 * H : 0.8875F + spin * 0.4 * 0.075;
      Vector3f tip = new Vector3f((float)(bx - dx * gap), (float)y, (float)(bz - dz * gap));
      Vector3f back = new Vector3f((float)(-dx * Math.cos(CUE_ELEVATION)), (float)Math.sin(CUE_ELEVATION), (float)(-dz * Math.cos(CUE_ELEVATION)));
      Vector3f butt = new Vector3f(back).mul(1.45F).add(tip);
      Vector3f side = new Vector3f(back).cross(0.0F, 1.0F, 0.0F).normalize();
      Vector3f up = new Vector3f(side).cross(back).normalize();
      int sides = 10;

      for (int k = 0; k < sides; k++) {
         double a0 = (Math.PI * 2) * k / sides;
         double a1 = (Math.PI * 2) * (k + 1) / sides;
         Vector3f n0 = ring(side, up, a0);
         Vector3f n1 = ring(side, up, a1);
         float u0 = (float)k / sides;
         float u1 = (float)(k + 1) / sides;
         cueVertex(vc, pose, tip, n0, 0.011, u0, 0.0F, light);
         cueVertex(vc, pose, butt, n0, 0.026, u0, 1.0F, light);
         cueVertex(vc, pose, butt, n1, 0.026, u1, 1.0F, light);
         cueVertex(vc, pose, tip, n1, 0.011, u1, 0.0F, light);
      }

      for (int k = 0; k < sides; k += 2) {
         Vector3f[] n = new Vector3f[]{
            ring(side, up, (Math.PI * 2) * k / sides), ring(side, up, (Math.PI * 2) * (k + 1) / sides), ring(side, up, (Math.PI * 2) * (k + 2) / sides)
         };
         cueCap(vc, pose, butt, back, new Vector3f(), 0.0, light);

         for (Vector3f dir : n) {
            cueCap(vc, pose, butt, back, dir, 0.026, light);
         }
      }
   }

   private static Vector3f ring(Vector3f side, Vector3f up, double a) {
      return new Vector3f(side).mul((float)Math.cos(a)).add(new Vector3f(up).mul((float)Math.sin(a)));
   }

   private static void cueVertex(VertexConsumer vc, Pose pose, Vector3f centre, Vector3f n, double r, float u, float v, int light) {
      vc.addVertex(pose, centre.x + n.x * (float)r, centre.y + n.y * (float)r, centre.z + n.z * (float)r)
         .setColor(-1)
         .setUv(u, v)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(light)
         .setNormal(pose, n.x, n.y, n.z);
   }

   private static void cueCap(VertexConsumer vc, Pose pose, Vector3f centre, Vector3f axis, Vector3f n, double r, int light) {
      vc.addVertex(pose, centre.x + n.x * (float)r, centre.y + n.y * (float)r, centre.z + n.z * (float)r)
         .setColor(-1)
         .setUv(0.5F, 0.995F)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(light)
         .setNormal(pose, axis.x, axis.y, axis.z);
   }

   private static void guide(PoolTableBlockEntity be, PoseStack ps, SubmitNodeCollector out) {
      PoolConfig.Guide mode = ClientAim.guide();
      if (mode != PoolConfig.Guide.OFF) {
         PoolSim sim = be.sim;
         double angle = ClientAim.angle;
         Guide g = Guide.cast(sim, angle);
         out.submitCustomGeometry(ps, RenderTypes.entityTranslucent(WHITE), (pose, vc) -> guide(sim, g, angle, mode, pose, vc));
      }
   }

   private static void guide(PoolSim sim, Guide g, double angle, PoolConfig.Guide mode, Pose pose, VertexConsumer vc) {
      double dx = Math.sin(angle);
      double dz = Math.cos(angle);
      double reach = edge(dx, dz);
      double ox = sim.x[0] + dx * reach;
      double oz = sim.z[0] + dz * reach;
      double ex = g.endX - dx * reach;
      double ez = g.endZ - dz * reach;
      if ((ex - ox) * dx + (ez - oz) * dz > 0.0) {
         line(vc, pose, ox, oz, ex, ez, 0.01, -1929379841);
      }

      if (cubes()) {
         square(vc, pose, g.endX, g.endZ, H, 0.007, -1258291201);
      } else {
         ring(vc, pose, g.endX, g.endZ, 0.075, 0.007, -1258291201);
      }

      if (mode == PoolConfig.Guide.FULL) {
         if (g.ball != -1) {
            double share = Math.hypot(g.objX, g.objZ);
            if (share > 0.001) {
               double nx = g.objX / share;
               double nz = g.objZ / share;
               double bx = sim.x[g.ball] + nx * edge(nx, nz);
               double bz = sim.z[g.ball] + nz * edge(nx, nz);
               double len = 0.12 + 0.55 * share;
               line(vc, pose, bx, bz, bx + nx * len, bz + nz * len, 0.01, -1258297716);
            }

            double cue = Math.hypot(g.cueX, g.cueZ);
            if (cue > 0.03) {
               double nx = g.cueX / cue;
               double nz = g.cueZ / cue;
               double sx = g.endX + nx * edge(nx, nz);
               double sz = g.endZ + nz * edge(nx, nz);
               double len = 0.35 * cue;
               line(vc, pose, sx, sz, sx + nx * len, sz + nz * len, 0.007, 1694498815);
            }
         } else {
            double len = 0.35;
            line(vc, pose, g.endX, g.endZ, g.endX + g.bounceX * len, g.endZ + g.bounceZ * len, 0.007, 1694498815);
         }
      }
   }

   private static void line(VertexConsumer vc, Pose pose, double x0, double z0, double x1, double z1, double width, int argb) {
      double dx = x1 - x0;
      double dz = z1 - z0;
      double len = Math.sqrt(dx * dx + dz * dz);
      if (!(len < 1.0E-4)) {
         double px = -dz / len * width / 2.0;
         double pz = dx / len * width / 2.0;
         int light = 15728880;
         quadVertex(vc, pose, (float)(x0 - px), 0.8165F, (float)(z0 - pz), 0.0F, 0.0F, argb, light);
         quadVertex(vc, pose, (float)(x0 + px), 0.8165F, (float)(z0 + pz), 0.0F, 1.0F, argb, light);
         quadVertex(vc, pose, (float)(x1 + px), 0.8165F, (float)(z1 + pz), 1.0F, 1.0F, argb, light);
         quadVertex(vc, pose, (float)(x1 - px), 0.8165F, (float)(z1 - pz), 1.0F, 0.0F, argb, light);
      }
   }

   private static void pocketRings(PoolTableBlockEntity be, TableVisuals v, PoseStack ps, SubmitNodeCollector out, double now, boolean active, Minecraft mc) {
      int call = -1;
      if (!be.moving() && active && ClientAim.aiming()) {
         call = ClientAim.call();
      } else if (!be.moving() && now - v.aimTime < 20.0 && v.aimPlayer != mc.player.getId()) {
         call = v.aimCall;
      }

      boolean onePocket = be.game.type == GameType.ONE_POCKET && be.game.phase != GameState.Phase.PRACTICE;
      if (call >= 0 || onePocket) {
         int called = call;
         out.submitCustomGeometry(ps, RenderTypes.entityTranslucent(WHITE), (pose, vc) -> {
            double[] p = TableDims.POCKETS;
            float y = 0.91225F;
            if (onePocket) {
               for (int seat = 0; seat < 2; seat++) {
                  int k = Rules.ownPocket(seat);
                  ring(vc, pose, p[k * 4], y, p[k * 4 + 1], p[k * 4 + 3] + 0.05, 0.02, SEAT_RING[seat]);
               }
            }

            if (called >= 0 && called < 6) {
               ring(vc, pose, p[called * 4], y, p[called * 4 + 1], p[called * 4 + 3] + 0.02, 0.03, -788532048);
            }
         });
      }
   }

   private static void ring(VertexConsumer vc, Pose pose, double cx, double cz, double r, double width, int argb) {
      ring(vc, pose, cx, 0.8165F, cz, r, width, argb);
   }

   private static void ring(VertexConsumer vc, Pose pose, double cx, float y, double cz, double r, double width, int argb) {
      int n = 28;

      for (int k = 0; k < n; k++) {
         double a0 = (Math.PI * 2) * k / n;
         double a1 = (Math.PI * 2) * (k + 1) / n;
         double ri = r - width / 2.0;
         double ro = r + width / 2.0;
         int light = 15728880;
         quadVertex(vc, pose, (float)(cx + Math.sin(a0) * ri), y, (float)(cz + Math.cos(a0) * ri), 0.0F, 0.0F, argb, light);
         quadVertex(vc, pose, (float)(cx + Math.sin(a0) * ro), y, (float)(cz + Math.cos(a0) * ro), 0.0F, 1.0F, argb, light);
         quadVertex(vc, pose, (float)(cx + Math.sin(a1) * ro), y, (float)(cz + Math.cos(a1) * ro), 1.0F, 1.0F, argb, light);
         quadVertex(vc, pose, (float)(cx + Math.sin(a1) * ri), y, (float)(cz + Math.cos(a1) * ri), 1.0F, 0.0F, argb, light);
      }
   }

   private static void square(VertexConsumer vc, Pose pose, double cx, double cz, double half, double width, int argb) {
      double out = half + width / 2.0;
      double in = half - width / 2.0;
      line(vc, pose, cx - out, cz - half, cx + out, cz - half, width, argb);
      line(vc, pose, cx - out, cz + half, cx + out, cz + half, width, argb);
      line(vc, pose, cx - half, cz - in, cx - half, cz + in, width, argb);
      line(vc, pose, cx + half, cz - in, cx + half, cz + in, width, argb);
   }

   public AABB getRenderBoundingBox(PoolTableBlockEntity be) {
      return be.frame().bounds().inflate(1.6, 1.0, 1.6);
   }

   public boolean shouldRenderOffScreen() {
      return true;
   }

   public int getViewDistance() {
      return 64;
   }

   public static class State extends BlockEntityRenderState {
      PoolTableBlockEntity be;
      float partialTick;
   }
}
