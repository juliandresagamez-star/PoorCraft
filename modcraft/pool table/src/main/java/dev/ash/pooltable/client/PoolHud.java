package dev.ash.pooltable.client;

import dev.ash.pooltable.PoolTable;
import dev.ash.pooltable.block.GameState;
import dev.ash.pooltable.block.PoolTableBlock;
import dev.ash.pooltable.block.PoolTableBlockEntity;
import dev.ash.pooltable.client.compat.ClientShim;
import dev.ash.pooltable.client.compat.GuiShim;
import dev.ash.pooltable.sim.EightBall;
import dev.ash.pooltable.sim.GameType;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import org.jetbrains.annotations.Nullable;

final class PoolHud {
   private static final Identifier ICONS = PoolTable.id("textures/gui/balls.png");
   private static final Identifier ICONS_ENGLISH = PoolTable.id("textures/gui/balls_english.png");
   private static final String TURN = "▶ ";
   private static final int[] SEAT_COLOUR = new int[]{-14276, -11482881};

   private PoolHud() {
   }

   static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
      Minecraft mc = Minecraft.getInstance();
      if (!ClientShim.get().hudHidden() && mc.player != null && mc.level != null && ClientShim.get().screen() == null) {
         PoolTableBlockEntity be = hudTable(mc);
         if (be != null) {
            Font font = mc.font;
            int w = g.guiWidth();
            int h = g.guiHeight();
            scoreboard(g, font, be);
            if (be == ClientAim.table()) {
               controls(g, font, be, w, h);
            }
         }
      }
   }

   @Nullable
   private static PoolTableBlockEntity hudTable(Minecraft mc) {
      PoolTableBlockEntity be = ClientAim.table();
      if (be != null) {
         return be;
      } else {
         HitResult hit = mc.hitResult;
         if (hit instanceof BlockHitResult bhr && hit.getType() == Type.BLOCK) {
            be = PoolTableBlock.master(mc.level, bhr.getBlockPos(), mc.level.getBlockState(bhr.getBlockPos()));
            if (be != null) {
               return be;
            }
         }

         be = PoolTableBlockEntity.nearest(mc.level, mc.player.getEyePosition(), 8.0);
         return be != null && be.game.seated(mc.player.getUUID()) ? be : null;
      }
   }

   static Component message(GameState game) {
      if (game.message.isEmpty()) {
         return Component.empty();
      } else {
         Object[] args = new Object[game.messageArgs.length];

         for (int i = 0; i < args.length; i++) {
            String a = game.messageArgs[i];
            args[i] = a.startsWith("#") ? Component.translatable(a.substring(1)) : a;
         }

         return PoolTable.text(game.message, args);
      }
   }

   private static void scoreboard(GuiGraphicsExtractor g, Font font, PoolTableBlockEntity be) {
      GameState game = be.game;
      int x = 4;
      int y = 4;
      int width = 150;
      List<FormattedCharSequence> msg = font.split(message(game), width - 8);
      if (game.phase == GameState.Phase.PRACTICE) {
         List<FormattedCharSequence> title = font.split(
            PoolTable.text("hud.practice_game", Component.translatable("pooltable.game." + game.type.key())), width - 8
         );
         g.fill(x, y, x + width, y + 6 + (title.size() + msg.size()) * 10, -1879048192);
         int ty = y + 4;

         for (FormattedCharSequence line : title) {
            g.text(font, line, x + 4, ty, -1513240);
            ty += 10;
         }

         for (FormattedCharSequence line : msg) {
            g.text(font, line, x + 4, ty, -3618616);
            ty += 10;
         }
      } else {
         GameType type = game.type;
         boolean lowestFirst = type == GameType.NINE || type == GameType.TEN;
         int rows = game.players();
         int rowH = lowestFirst ? 12 : 22;
         int top = y + 4 + rows * rowH + (lowestFirst ? 12 : 0);
         g.fill(x, y, x + width, top + 2 + msg.size() * 10, -1879048192);

         for (int s = 0; s < rows; s++) {
            int ry = y + 4 + s * rowH;
            boolean up = game.inGame() && game.turn == s;
            boolean won = game.phase == GameState.Phase.OVER && game.winner >= 0
               && (game.teams() ? game.teamOf(game.winner) == game.teamOf(s) : game.winner == s);
            String name = game.name[s].isEmpty() ? "-" : game.name[s];
            int color = won ? -10166 : (up ? -1 : -6250336);
            Component player = game.teams()
               ? PoolTable.text("hud.team_player", game.teamOf(s) + 1, name)
               : Component.literal(name);
            g.text(font, (up ? "▶ " : ""), x + 4, ry, color);
            g.text(font, player, x + 13, ry, color);
            String wins = String.valueOf(game.wins[s]);
            g.text(font, wins, x + width - 6 - font.width(wins), ry, -1513240);
            if (!lowestFirst) {
               detail(g, font, be, s, x + 14, ry + 10);
            }
         }

         if (lowestFirst) {
            int ry = y + 4 + rows * rowH;
            Component on = PoolTable.text("hud.on");
            g.text(font, on, x + 14, ry + 1, -8355712);
            int shown = 0;

            for (int ball = 1; ball < 16; ball++) {
               if (be.sim.on[ball]) {
                  GuiShim.get().blit(g, icons(game), x + 18 + font.width(on) + shown++ * 9, ry, ball * 9, 0.0F, 9, 9, 144, 9);
               }
            }
         }

         int ty = top;

         for (FormattedCharSequence line : msg) {
            g.text(font, line, x + 4, ty, -5720);
            ty += 10;
         }
      }
   }

   private static void detail(GuiGraphicsExtractor g, Font font, PoolTableBlockEntity be, int seat, int x, int y) {
      GameState game = be.game;
      if (game.type.points()) {
         Component points = PoolTable.text("hud.points", game.score[seat], game.pointsToWin());
         g.text(font, points, x, y + 1, -2039584);
         if (game.type == GameType.ONE_POCKET) {
            int px = x + font.width(points) + 6;
            g.fill(px, y + 2, px + 6, y + 8, SEAT_COLOUR[game.teamOf(seat)]);
            g.text(font, PoolTable.text(game.teamOf(seat) == 0 ? "hud.pocket.left" : "hud.pocket.right"), px + 9, y + 1, -6250336);
         }
      } else {
         balls(g, font, be, game.group[seat], x, y);
      }
   }

   private static Identifier icons(GameState game) {
      return game.type == GameType.ENGLISH_8 ? ICONS_ENGLISH : ICONS;
   }

   private static void balls(GuiGraphicsExtractor g, Font font, PoolTableBlockEntity be, EightBall.Group group, int x, int y) {
      if (group == EightBall.Group.NONE) {
         g.text(font, PoolTable.text("hud.open"), x, y + 1, -8355712);
      } else {
         Identifier icons = icons(be.game);
         int shown = 0;

         for (int ball = 1; ball < 16; ball++) {
            if (group.has(ball) && be.sim.on[ball]) {
               GuiShim.get().blit(g, icons, x + shown++ * 9, y, ball * 9, 0.0F, 9, 9, 144, 9);
            }
         }

         if (shown == 0 && be.game.type.eightBall() && be.sim.on[8]) {
            GuiShim.get().blit(g, icons, x, y, 72.0F, 0.0F, 9, 9, 144, 9);
         } else if (shown == 0 && be.game.type == GameType.CUTTHROAT) {
            g.text(font, PoolTable.text("hud.out"), x, y + 1, -8355712);
         }
      }
   }

   private static void controls(GuiGraphicsExtractor g, Font font, PoolTableBlockEntity be, int w, int h) {
      boolean aim = ClientAim.canAim();
      boolean place = ClientAim.canPlace();
      int cx = w / 2;
      int cy = h / 2;
      boolean steppedUp = ClientAim.aiming();
      if (ClientAim.charging() && steppedUp) {
         int bh = 50;
         int bx = cx + 14;
         int by = cy - bh / 2;
         g.fill(bx - 1, by - 1, bx + 6, by + bh + 1, -1610612736);
         float fill = ClientAim.power * bh;

         for (int i = 0; i < bh && i < fill; i++) {
            float t = (float)i / bh;
            int r = (int)(Math.min(1.0F, t * 2.0F) * 255.0F);
            int gr = (int)(Math.min(1.0F, 2.0F - t * 2.0F) * 220.0F);
            int alpha = (int)(Math.min(1.0F, fill - i) * 255.0F);
            g.fill(bx, by + bh - 1 - i, bx + 5, by + bh - i, alpha << 24 | r << 16 | gr << 8 | 40);
         }
      }

      if (steppedUp) {
         int sx = w - 40;
         int sy = h - 44;
         g.fill(sx - 4, sy - 16, sx + 31, sy + 31, -1879048192);
         g.pose().pushMatrix();
         g.pose().translate(sx, sy);
         g.pose().scale(3.0F, 3.0F);
         GuiShim.get().blit(g, icons(be.game), 0, 0, 0.0F, 0.0F, 9, 9, 144, 9);
         g.pose().popMatrix();
         int dotY = sy + 12 - Math.round(ClientAim.spin * 9.0F);
         g.fill(sx + 12, dotY, sx + 15, dotY + 3, -3137504);
         Component label = PoolTable.text("hud.spin");
         g.text(font, label, sx + 14 - font.width(label) / 2, sy - 13, -4144960);
      }

      ClientAim.View view = ClientAim.view();
      Component hint;
      if (AimCamera.watching()) {
         hint = PoolTable.text("hud.hint.watch");
      } else {
         if (be.moving()) {
            return;
         }

         if (view == ClientAim.View.PLACE) {
            hint = PoolTable.text(be.ballInHand() == EightBall.BallInHand.KITCHEN ? "hud.hint.in_hand_kitchen" : "hud.hint.in_hand");
         } else if (view == ClientAim.View.AIM_CAMERA) {
            hint = PoolTable.text("hud.hint.camera");
         } else if (view == ClientAim.View.AIM_EYES) {
            hint = PoolTable.text(VrInput.active() ? "hud.hint.aim_vr" : "hud.hint.aim");
         } else if (!aim && !place) {
            if (be.game.phase == GameState.Phase.OVER) {
               hint = PoolTable.text("hud.hint.over");
            } else {
               if (!be.game.inGame()) {
                  return;
               }

               hint = PoolTable.text("hud.hint.wait", be.game.name[be.game.turn]);
            }
         } else {
            hint = PoolTable.text(
               be.game.phase == GameState.Phase.PRACTICE
                  ? (place ? "hud.hint.step_up_practice" : "hud.hint.step_up")
                  : (place ? "hud.hint.step_up_in_hand" : "hud.hint.step_up")
            );
         }
      }

      int hw = font.width(hint);
      g.fill(cx - hw / 2 - 4, h - 88, cx + hw / 2 + 4, h - 76, 1879048192);
      g.text(font, hint, cx - hw / 2, h - 86, -2039584);
      if (steppedUp && ClientAim.callNeeded()) {
         Component call = PoolTable.text("hud.hint.call");
         int cw = font.width(call);
         g.fill(cx - cw / 2 - 4, h - 101, cx + cw / 2 + 4, h - 89, 1879048192);
         g.text(font, call, cx - cw / 2, h - 99, -2896);
      }
   }
}
