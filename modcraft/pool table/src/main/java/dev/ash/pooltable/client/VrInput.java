package dev.ash.pooltable.client;

import java.lang.reflect.Method;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VrInput {
   private static final Logger LOG = LoggerFactory.getLogger("pooltable");
   public static double testYaw = Double.NaN;
   public static double testPitch = -20.0;
   public static float[] testLastPulse;
   private static boolean looked;
   private static boolean failed;
   private static Object api;
   private static Method vrActive;
   private static Method worldPose;
   private static Method mainHand;
   private static Method yaw;
   private static Method pitch;
   private static boolean extrasLooked;
   private static boolean pointerFailed;
   private static boolean passFailed;
   private static boolean hapticFailed;
   private static Method handPos;
   private static Method handDir;
   private static Method renderPass;
   private static Method haptic;
   private static Object renderingApi;
   private static Object mainHandPart;

   private VrInput() {
   }

   public static boolean active() {
      if (!Double.isNaN(testYaw)) {
         return true;
      } else if (!find()) {
         return false;
      } else {
         try {
            return (Boolean)vrActive.invoke(api);
         } catch (RuntimeException | LinkageError | ReflectiveOperationException var1) {
            return fail(var1);
         }
      }
   }

   static double[] hand() {
      if (!Double.isNaN(testYaw)) {
         return new double[]{Math.toRadians(testYaw), Math.toRadians(testPitch)};
      } else if (!active()) {
         return null;
      } else {
         try {
            Object pose = worldPose.invoke(api);
            Object hand = pose == null ? null : mainHand.invoke(pose);
            if (hand == null) {
               return null;
            } else {
               double y = (Double)yaw.invoke(hand);
               double p = (Double)pitch.invoke(hand);
               return Double.isFinite(y) && Double.isFinite(p) ? new double[]{y, p} : null;
            }
         } catch (RuntimeException | LinkageError | ReflectiveOperationException var6) {
            fail(var6);
            return null;
         }
      }
   }

   static Vec3 handDir() {
      if (!Double.isNaN(testYaw)) {
         double y = Math.toRadians(testYaw);
         double p = Math.toRadians(testPitch);
         return new Vec3(-Math.sin(y) * Math.cos(p), Math.sin(p), Math.cos(y) * Math.cos(p));
      } else {
         Vec3 dir = handVec(true);
         return dir != null && !(dir.lengthSqr() < 1.0E-6) ? dir.normalize() : null;
      }
   }

   static Vec3 handPos() {
      return Double.isNaN(testYaw) ? handVec(false) : null;
   }

   private static Vec3 handVec(boolean direction) {
      if (active() && extras() && !pointerFailed) {
         try {
            Object pose = worldPose.invoke(api);
            Object hand = pose == null ? null : mainHand.invoke(pose);
            return (hand == null ? null : (direction ? handDir : handPos).invoke(hand)) instanceof Vec3 vec
                  && Double.isFinite(vec.x)
                  && Double.isFinite(vec.y)
                  && Double.isFinite(vec.z)
               ? vec
               : null;
         } catch (RuntimeException | LinkageError | ReflectiveOperationException var5) {
            pointerFailed = true;
            LOG.warn("Couldn't read where Vivecraft's controller points; placing the cue ball in VR follows your eyes", var5);
            return null;
         }
      } else {
         return null;
      }
   }

   static Boolean drawingWorld() {
      if (Double.isNaN(testYaw) && active() && extras() && !passFailed) {
         try {
            return renderPass.invoke(renderingApi) instanceof Enum<?> e ? !e.name().equals("GUI") : null;
         } catch (RuntimeException | LinkageError | ReflectiveOperationException var2) {
            passFailed = true;
            LOG.warn("Couldn't ask Vivecraft which render pass it's in; the held cue may show while lining up in VR", var2);
            return null;
         }
      } else {
         return null;
      }
   }

   static void pulse(float seconds, float strength) {
      strength = Math.max(0.0F, Math.min(1.0F, strength));
      if (!Double.isNaN(testYaw)) {
         testLastPulse = new float[]{seconds, strength};
      } else if (active() && extras() && !hapticFailed) {
         testLastPulse = new float[]{seconds, strength};

         try {
            haptic.invoke(api, mainHandPart, seconds, 160.0F, strength, 0.0F);
         } catch (RuntimeException | LinkageError | ReflectiveOperationException var3) {
            hapticFailed = true;
            LOG.warn("Couldn't buzz Vivecraft's controller; carrying on without", var3);
         }
      }
   }

   public static String apiReport() {
      if (!find()) {
         return "no Vivecraft";
      } else {
         extras();

         String pass;
         try {
            pass = passFailed ? "missing" : "ok (" + renderPass.invoke(renderingApi) + ")";
         } catch (RuntimeException | LinkageError | ReflectiveOperationException var4) {
            pass = "failed: " + var4;
         }

         String buzz;
         try {
            if (!hapticFailed) {
               haptic.invoke(api, mainHandPart, 0.01F, 160.0F, 0.0F, 0.0F);
            }

            buzz = hapticFailed ? "missing" : "ok";
         } catch (RuntimeException | LinkageError | ReflectiveOperationException var3) {
            buzz = "failed: " + var3;
         }

         return "aiming ok, controller position and direction " + (pointerFailed ? "missing" : "ok") + ", render pass " + pass + ", haptics " + buzz;
      }
   }

   private static boolean extras() {
      if (!extrasLooked) {
         extrasLooked = true;
         ClassLoader loader = VrInput.class.getClassLoader();

         try {
            Class<?> part = Class.forName("org.vivecraft.api.data.VRBodyPartData", false, loader);
            handPos = part.getMethod("getPos");
            handDir = part.getMethod("getDir");
         } catch (RuntimeException | LinkageError | ReflectiveOperationException var7) {
            pointerFailed = true;
         }

         try {
            Class<?> rendering = Class.forName("org.vivecraft.api.client.VRRenderingAPI", false, loader);
            renderPass = rendering.getMethod("getCurrentRenderPass");
            renderingApi = rendering.getMethod("instance").invoke(null);
         } catch (RuntimeException | LinkageError | ReflectiveOperationException var6) {
            passFailed = true;
         }

         try {
            Class<?> bodyPart = Class.forName("org.vivecraft.api.data.VRBodyPart", false, loader);
            haptic = Class.forName("org.vivecraft.api.client.VRClientAPI", false, loader)
               .getMethod("triggerHapticPulse", bodyPart, float.class, float.class, float.class, float.class);

            for (Object constant : bodyPart.getEnumConstants()) {
               if (((Enum)constant).name().equals("MAIN_HAND")) {
                  mainHandPart = constant;
               }
            }

            hapticFailed = mainHandPart == null;
         } catch (RuntimeException | LinkageError | ReflectiveOperationException var8) {
            hapticFailed = true;
         }
      }

      return true;
   }

   private static boolean find() {
      if (!looked) {
         looked = true;
         ClassLoader loader = VrInput.class.getClassLoader();

         try {
            Class<?> client = Class.forName("org.vivecraft.api.client.VRClientAPI", false, loader);
            Class<?> pose = Class.forName("org.vivecraft.api.data.VRPose", false, loader);
            Class<?> part = Class.forName("org.vivecraft.api.data.VRBodyPartData", false, loader);
            vrActive = client.getMethod("isVRActive");
            worldPose = client.getMethod("getWorldRenderPose");
            mainHand = pose.getMethod("getMainHand");
            yaw = part.getMethod("getYaw");
            pitch = part.getMethod("getPitch");
            api = client.getMethod("instance").invoke(null);
            LOG.info("Vivecraft found: in VR, lining up a pool shot follows the main controller");
         } catch (ClassNotFoundException var4) {
            api = null;
         } catch (RuntimeException | LinkageError | ReflectiveOperationException var5) {
            fail(var5);
         }
      }

      return api != null;
   }

   private static boolean fail(Throwable e) {
      if (!failed) {
         failed = true;
         LOG.warn("Couldn't read Vivecraft's controllers; aiming in VR won't follow them", e);
      }

      api = null;
      looked = true;
      return false;
   }
}
