package dev.ash.pooltable.client;

import dev.ash.pooltable.PoolTable;
import dev.ash.pooltable.block.PoolTableBlock;
import dev.ash.pooltable.block.PoolTableBlockEntity;
import dev.ash.pooltable.block.TableFrame;
import dev.ash.pooltable.client.compat.ClientShim;
import dev.ash.pooltable.mixin.ClientInputAccess;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;

public final class PoolClientEvents {
   static final Map<DyeColor, Integer> FELT = new EnumMap<>(DyeColor.class);

   private PoolClientEvents() {
   }

   public static void setup() {
      PoolTable.clientHooks = new PoolClientHooks();
   }

   @Nullable
   public static ArmPose armPose(LivingEntity entity) {
      return entity instanceof Player p && ClientAim.isAiming(p) ? ArmPose.CROSSBOW_HOLD : null;
   }

   public static int clothColour(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos) {
      DyeColor color = DyeColor.GREEN;
      if (level != null
         && pos != null
         && state.getBlock() instanceof PoolTableBlock
         && level.getBlockEntity(TableFrame.of(pos, state).master()) instanceof PoolTableBlockEntity be) {
         color = be.clothColor();
      }

      return FELT.get(color);
   }

   public static void hud(GuiGraphicsExtractor g, DeltaTracker delta) {
      PoolHud.render(g, delta);
   }

   public static void tickStart() {
      ClientAim.tickStart(Minecraft.getInstance());
   }

   public static void tickEnd() {
      ClientAim.tick(Minecraft.getInstance());
      AimCamera.tick(Minecraft.getInstance());
   }

   public static void frameStart(float partialTick) {
      ClientAim.worldPass = true;
      ClientAim.frame(partialTick);
      AimCamera.frame(Minecraft.getInstance());
   }

   public static void guiStart() {
      ClientAim.worldPass = false;
   }

   public static boolean useKey() {
      return ClientAim.useKey();
   }

   public static boolean attackKey() {
      return ClientAim.attackKey();
   }

   public static boolean pickKey() {
      return ClientShim.get().screen() == null && ClientAim.pickKey();
   }

   public static boolean scrolled(double delta) {
      return ClientShim.get().screen() == null && ClientAim.scrolled(delta);
   }

   public static void movement(ClientInput input) {
      if (ClientAim.freezeMovement()) {
         input.keyPresses = new Input(false, false, false, false, false, input.keyPresses.shift(), false);
         ((ClientInputAccess)input).pooltable$setMoveVector(Vec2.ZERO);
      }
   }

   public static boolean hideCrosshair() {
      return AimCamera.active();
   }

   public static boolean hideHand() {
      return ClientAim.hideHand() || AimCamera.active();
   }

   public static boolean hideEntity(Entity entity) {
      return entity instanceof LivingEntity && AimCamera.hides(entity, ClientShim.get().mainCamera().position());
   }

   public static boolean hideOutline() {
      return AimCamera.active();
   }

   @Nullable
   public static AABB tableOutline(BlockPos pos, BlockState state) {
      if (!(state.getBlock() instanceof PoolTableBlock)) {
         return null;
      } else {
         AABB box = TableFrame.of(pos, state).bounds();
         return box.setMaxY(box.minY + 0.90625);
      }
   }

   public static void loggedOut() {
      ClientAim.reset();
   }

   static {
      FELT.put(DyeColor.WHITE, 15000282);
      FELT.put(DyeColor.ORANGE, 13658142);
      FELT.put(DyeColor.MAGENTA, 10498174);
      FELT.put(DyeColor.LIGHT_BLUE, 4099791);
      FELT.put(DyeColor.YELLOW, 13214762);
      FELT.put(DyeColor.LIME, 5219386);
      FELT.put(DyeColor.PINK, 14250140);
      FELT.put(DyeColor.GRAY, 5790818);
      FELT.put(DyeColor.LIGHT_GRAY, 10133154);
      FELT.put(DyeColor.CYAN, 1869446);
      FELT.put(DyeColor.PURPLE, 6171788);
      FELT.put(DyeColor.BLUE, 2051742);
      FELT.put(DyeColor.BROWN, 8016432);
      FELT.put(DyeColor.GREEN, 1997374);
      FELT.put(DyeColor.RED, 10690340);
      FELT.put(DyeColor.BLACK, 2763310);
   }
}
