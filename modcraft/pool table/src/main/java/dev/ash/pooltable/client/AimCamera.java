package dev.ash.pooltable.client;

import dev.ash.pooltable.block.PoolTableBlockEntity;
import dev.ash.pooltable.block.TableFrame;
import dev.ash.pooltable.sim.PoolSim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.jetbrains.annotations.Nullable;

public final class AimCamera {
   private static final double DISTANCE = 1.35;
   private static final double LOOK_AHEAD = 0.4;
   private static final double BLEND_SECONDS = 0.5;
   private static final double WATCH_DELAY = 0.1;
   private static final double WATCH_MOVE_SECONDS = 1.3;
   private static final float WATCH_PITCH = 55.0F;
   private static final float MIN_PITCH = 15.0F;
   private static final float MAX_PITCH = 89.0F;
   private static final double FRAME_MARGIN = 0.88;
   private static final double LINGER_SECONDS = 0.6;
   private static final double OUT_AFTER_WATCH_SECONDS = 0.8;
   private static final int WATCH_TIMEOUT = 40;
   private static final double ORBIT_SMOOTH_SECONDS = 0.06;
   private static final double FIT_SMOOTH_SECONDS = 0.15;
   private static final double ZOOM_STEP = 1.15;
   private static final double MIN_ZOOM = 0.5;
   private static final double MAX_ZOOM = 1.8;
   private static final double SWITCH_SECONDS = 0.6;
   private static final double CLEARANCE = 0.2;
   private static final double CLOSE_IN_SECONDS = 0.04;
   private static final double OPEN_OUT_SECONDS = 0.25;
   private static float blend;
   private static AimCamera.Mode mode = AimCamera.Mode.NONE;
   private static AimCamera.Mode lastMode = AimCamera.Mode.NONE;
   @Nullable
   private static AimCamera.Pose camera;
   @Nullable
   private static Vec3 lookPoint;
   @Nullable
   private static AimCamera.Pose fromPose;
   private static long switchStart;
   @Nullable
   private static AimCamera.Aim held;
   @Nullable
   private static PoolTableBlockEntity watching;
   private static boolean sawMoving;
   private static int watchTicks;
   private static long watchStart;
   private static long stoppedAt;
   @Nullable
   private static AimCamera.Aim shot;
   private static float sideYaw;
   private static float orbitYaw;
   private static float orbitYawTarget;
   private static float orbitPitch;
   private static float orbitPitchTarget;
   private static double zoom = 1.0;
   private static double zoomTarget = 1.0;
   private static double shownFit = Double.NaN;
   private static long lastNanos;
   private static double shownReach = Double.NaN;

   private AimCamera() {
   }

   static void watch(PoolTableBlockEntity be) {
      TableFrame f = be.frame();
      Vec3 centre = centre(f);
      LocalPlayer player = Minecraft.getInstance().player;
      watching = be;
      sawMoving = false;
      watchTicks = 0;
      stoppedAt = 0L;
      watchStart = System.nanoTime();
      orbitPitchTarget = 0.0F;
      orbitPitch = 0.0F;
      orbitYawTarget = 0.0F;
      orbitYaw = 0.0F;
      zoomTarget = 1.0;
      zoom = 1.0;
      shownFit = Double.NaN;
      shot = held != null ? held : new AimCamera.Aim(centre, centre, player == null ? 0.0F : player.getYRot(), 55.0F);
      sideYaw = sideYaw(f, shot.yaw(), player);
   }

   static void stopWatching() {
      watching = null;
   }

   static void orbit(double dx, double dy) {
      orbitYawTarget += (float)(dx * 0.15);
      orbitPitchTarget = Mth.clamp(orbitPitchTarget + (float)(dy * 0.15), -40.0F, 34.0F);
   }

   static void zoom(double delta) {
      zoomTarget = Mth.clamp(delta > 0.0 ? zoomTarget / 1.15 : zoomTarget * 1.15, 0.5, 1.8);
   }

   private static float sideYaw(TableFrame f, float from, @Nullable LocalPlayer player) {
      Direction across = f.facing().getCounterClockWise();
      float toPlus = (float)Math.toDegrees(Math.atan2(-across.getStepX(), across.getStepZ()));
      float toMinus = Mth.wrapDegrees(toPlus + 180.0F);
      float a = Math.abs(Mth.wrapDegrees(toPlus - from));
      float b = Math.abs(Mth.wrapDegrees(toMinus - from));
      if (Math.abs(a - b) < 20.0F && player != null) {
         return f.localX(player.position()) <= 0.0 ? toPlus : toMinus;
      } else {
         return a < b ? toPlus : toMinus;
      }
   }

   public static boolean active() {
      return blend > 0.0F;
   }

   public static boolean watching() {
      return watching != null;
   }

   public static float blend() {
      return blend;
   }

   static void reset() {
      blend = 0.0F;
      mode = lastMode = AimCamera.Mode.NONE;
      held = null;
      watching = null;
      camera = null;
      lookPoint = null;
      fromPose = null;
      shownReach = Double.NaN;
   }

   static void tick(Minecraft mc) {
      if (watching != null) {
         watchTicks++;
         if (watching.isRemoved()) {
            watching = null;
         } else if (watching.moving()) {
            sawMoving = true;
            stoppedAt = 0L;
         } else if (sawMoving) {
            long now = System.nanoTime();
            if (stoppedAt == 0L) {
               stoppedAt = now;
            } else if (now - stoppedAt > 6.0E8) {
               watching = null;
            }
         } else if (watchTicks > 40) {
            watching = null;
         }
      }
   }

   static void frame(Minecraft mc) {
      long now = System.nanoTime();
      double dt = lastNanos == 0L ? 0.0 : Math.min((now - lastNanos) / 1.0E9, 0.1);
      lastNanos = now;
      AimCamera.Mode want = mc.player == null
         ? AimCamera.Mode.NONE
         : (watching != null ? AimCamera.Mode.WATCH : (ClientAim.view() == ClientAim.View.AIM_CAMERA ? AimCamera.Mode.AIM : AimCamera.Mode.NONE));
      if (want != AimCamera.Mode.NONE && want != mode && blend > 0.0F && camera != null && (mode != AimCamera.Mode.AIM || want != AimCamera.Mode.WATCH)) {
         fromPose = camera;
         switchStart = now;
      }

      mode = want;
      if (want != AimCamera.Mode.NONE) {
         lastMode = want;
      }

      double ease = want == AimCamera.Mode.NONE && lastMode == AimCamera.Mode.WATCH ? 0.8 : 0.5;
      blend = Mth.approach(blend, want != AimCamera.Mode.NONE ? 1.0F : 0.0F, (float)(dt / ease));
      if (blend <= 0.0F) {
         camera = null;
         lookPoint = null;
         fromPose = null;
         held = null;
         shownReach = Double.NaN;
         lastMode = AimCamera.Mode.NONE;
      } else if (want != AimCamera.Mode.NONE) {
         AimCamera.Pose next = want == AimCamera.Mode.AIM ? aimPose(dt) : watchPose(now, dt);
         if (next != null) {
            if (fromPose != null) {
               double s = (now - switchStart) / 1.0E9 / 0.6;
               if (s >= 1.0) {
                  fromPose = null;
               } else {
                  float e = smooth((float)s);
                  next = new AimCamera.Pose(
                     fromPose.pos().lerp(next.pos(), e),
                     fromPose.yaw() + Mth.wrapDegrees(next.yaw() - fromPose.yaw()) * e,
                     Mth.lerp(e, fromPose.pitch(), next.pitch())
                  );
               }
            }

            camera = next;
         }
      }
   }

   @Nullable
   private static AimCamera.Pose aimPose(double dt) {
      AimCamera.Aim aim = aim(ClientAim.table());
      if (aim != null) {
         held = aim;
      }

      if (held == null) {
         return null;
      } else {
         lookPoint = held.target();
         return place(held.target(), held.ball(), held.yaw(), held.pitch(), 1.35, dt);
      }
   }

   @Nullable
   private static AimCamera.Pose watchPose(long now, double dt) {
      PoolTableBlockEntity be = watching;
      if (be != null && !be.isRemoved() && shot != null) {
         TableFrame f = be.frame();
         Vec3 centre = centre(f);
         float r = smooth((float)Mth.clamp(((now - watchStart) / 1.0E9 - 0.1) / 1.3, 0.0, 1.0));
         double k = 1.0 - Math.exp(-dt / 0.06);
         orbitYaw = orbitYaw + (orbitYawTarget - orbitYaw) * (float)k;
         orbitPitch = orbitPitch + (orbitPitchTarget - orbitPitch) * (float)k;
         zoom = zoom + (zoomTarget - zoom) * k;
         float yaw = shot.yaw() + Mth.wrapDegrees(sideYaw - shot.yaw()) * r + orbitYaw;
         float pitch = Mth.clamp(Mth.lerp(r, shot.pitch(), 55.0F) + orbitPitch, 15.0F, 89.0F);
         double fit = fitDistance(f, centre, yaw, pitch) * zoom;
         shownFit = Double.isNaN(shownFit) ? fit : shownFit + (fit - shownFit) * (1.0 - Math.exp(-dt / 0.15));
         Vec3 focus = shot.target().lerp(centre, r);
         lookPoint = focus;
         return place(focus, shot.ball().lerp(centre, r), yaw, pitch, Mth.lerp(r, 1.35, shownFit), dt);
      } else {
         return null;
      }
   }

   @Nullable
   public static AimCamera.Pose pose(Entity entity, float partialTick, Vec3 eye, float eyeYaw, float eyePitch) {
      Minecraft mc = Minecraft.getInstance();
      if (entity == mc.player && camera != null && !(blend <= 0.0F)) {
         float s = smooth(blend);
         Vec3 pos = eye.lerp(camera.pos(), s);
         float yaw = eyeYaw + Mth.wrapDegrees(camera.yaw() - eyeYaw) * s;
         float pitch = Mth.lerp(s, eyePitch, camera.pitch());
         return new AimCamera.Pose(pos, yaw, pitch);
      } else {
         return null;
      }
   }

   private static float smooth(float x) {
      return x * x * (3.0F - 2.0F * x);
   }

   private static Vec3 centre(TableFrame f) {
      return f.toWorld(0.0, 0.8875, 0.0);
   }

   @Nullable
   private static AimCamera.Aim aim(@Nullable PoolTableBlockEntity be) {
      if (be == null) {
         return null;
      } else {
         TableFrame f = be.frame();
         PoolSim sim = be.sim;
         double bx = sim.x[0];
         double bz = sim.z[0];
         double dx = Math.sin(ClientAim.angle);
         double dz = Math.cos(ClientAim.angle);
         double y = 0.8875;
         Vec3 ball = f.toWorld(bx, y, bz);
         Vec3 target = f.toWorld(bx + dx * 0.4, y, bz + dz * 0.4);
         Vec3 dir = target.subtract(ball).normalize();
         float yaw = (float)Math.toDegrees(Math.atan2(-dir.x, dir.z));
         return new AimCamera.Aim(target, ball, yaw, ClientAim.tilt);
      }
   }

   private static Vec3 look(float yaw, float pitch) {
      double y = Math.toRadians(yaw);
      double p = Math.toRadians(pitch);
      return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
   }

   private static double fitDistance(TableFrame f, Vec3 centre, float yaw, float pitch) {
      Minecraft mc = Minecraft.getInstance();
      double tv = Math.tan(Math.toRadians(((Integer)mc.options.fov().get()).intValue()) / 2.0) * 0.88;
      double th = tv * mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
      Vec3 look = look(yaw, pitch);
      double y = Math.toRadians(yaw);
      Vec3 right = new Vec3(Math.cos(y), 0.0, Math.sin(y));
      Vec3 up = right.cross(look);
      double d = 1.35;

      for (int sx = -1; sx <= 1; sx += 2) {
         for (int sz = -1; sz <= 1; sz += 2) {
            Vec3 q = f.toWorld(sx * 1.5, 0.90625, sz * 2.5).subtract(centre);
            d = Math.max(d, Math.max(Math.abs(q.dot(right)) / th, Math.abs(q.dot(up)) / tv) - q.dot(look));
         }
      }

      return d;
   }

   private static AimCamera.Pose place(Vec3 focus, Vec3 pivot, float yaw, float pitch, double distance, double dt) {
      Vec3 spot = focus.subtract(look(yaw, pitch).scale(distance));
      LocalPlayer player = Minecraft.getInstance().player;
      Vec3 from = pivot.add(0.0, 0.35, 0.0);
      Vec3 out = spot.subtract(from);
      double length = out.length();
      double reach = 1.0;
      if (length > 1.0E-4 && player != null) {
         Vec3 end = spot.add(out.scale(0.2 / length));
         HitResult hit = player.level().clip(new ClipContext(from, end, Block.VISUAL, Fluid.NONE, player));
         if (hit.getType() != Type.MISS) {
            reach = Mth.clamp((hit.getLocation().distanceTo(from) - 0.2) / length, 0.0, 1.0);
         }
      }

      if (Double.isNaN(shownReach)) {
         shownReach = reach;
      } else {
         double seconds = reach < shownReach ? 0.04 : 0.25;
         shownReach = shownReach + (reach - shownReach) * (1.0 - Math.exp(-dt / seconds));
      }

      return new AimCamera.Pose(from.add(out.scale(shownReach)), yaw, pitch);
   }

   public static boolean hides(Entity entity, Vec3 cameraPos) {
      if (active() && lookPoint != null) {
         AABB box = entity.getBoundingBox().inflate(0.4);
         if (!box.contains(cameraPos) && !(distance(box, cameraPos) < 1.2)) {
            Vec3 view = lookPoint.subtract(cameraPos);
            return view.lengthSqr() < 1.0E-6 ? false : box.clip(cameraPos, lookPoint.add(view.normalize().scale(0.5))).isPresent();
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   private static double distance(AABB box, Vec3 p) {
      double dx = Math.max(0.0, Math.max(box.minX - p.x, p.x - box.maxX));
      double dy = Math.max(0.0, Math.max(box.minY - p.y, p.y - box.maxY));
      double dz = Math.max(0.0, Math.max(box.minZ - p.z, p.z - box.maxZ));
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   private record Aim(Vec3 target, Vec3 ball, float yaw, float pitch) {
   }

   private static enum Mode {
      NONE,
      AIM,
      WATCH;
   }

   public record Pose(Vec3 pos, float yaw, float pitch) {
   }
}
