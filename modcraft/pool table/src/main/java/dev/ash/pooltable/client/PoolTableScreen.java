package dev.ash.pooltable.client;

import dev.ash.pooltable.PoolClientConfig;
import dev.ash.pooltable.PoolConfig;
import dev.ash.pooltable.PoolTable;
import dev.ash.pooltable.block.GameState;
import dev.ash.pooltable.block.PoolTableBlockEntity;
import dev.ash.pooltable.net.Net;
import dev.ash.pooltable.net.TableActionPayload;
import dev.ash.pooltable.sim.EightBall;
import dev.ash.pooltable.sim.GameType;
import dev.ash.pooltable.sim.PoolBot;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class PoolTableScreen extends Screen {
   public static final int W = 300;
   public static final int H = 312;
   public static final int GAME_Y = 23;
   public static final int SEAT_Y = 47;
   public static final int SEAT_H = 22;
   public static final int BOT_Y = 160;
   public static final int FORMAT_Y = 184;
   public static final int STATUS_Y = 210;
   public static final int BUTTONS_Y = 222;
   public static final int HELP_Y = 251;
   public static final int SETTINGS_Y = 281;
   private static PoolBot.Level botChoice = PoolBot.Level.MEDIUM;
   private final BlockPos pos;
   private final Button[] seatButtons = new Button[GameState.SEATS];
   private Button game;
   private Button target;
   private Button rack;
   private Button concede;
   private Button camera;
   private Button shape;
   private Button guide;
   private Button botLevel;
   private Button botSeat;
   private Button format;
   private int left;
   private int top;

   public PoolTableScreen(BlockPos pos) {
      super(PoolTable.text("screen.title"));
      this.pos = pos;
   }

   @Nullable
   private PoolTableBlockEntity table() {
      return this.minecraft != null && this.minecraft.level != null && this.minecraft.level.getBlockEntity(this.pos) instanceof PoolTableBlockEntity be
         ? be
         : null;
   }

   private UUID me() {
      return this.minecraft.player.getUUID();
   }

   private float uiScale() {
      return Math.min(1.0F, Math.min((this.width - 16.0F) / W, (this.height - 16.0F) / H));
   }

   private double logicalX(double x) {
      return this.width / 2.0 + (x - this.width / 2.0) / this.uiScale();
   }

   private double logicalY(double y) {
      return this.height / 2.0 + (y - this.height / 2.0) / this.uiScale();
   }

   private MouseButtonEvent logicalMouse(MouseButtonEvent event) {
      return new MouseButtonEvent(this.logicalX(event.x()), this.logicalY(event.y()), event.buttonInfo());
   }

   private void pushUiScale(GuiGraphicsExtractor graphics) {
      graphics.pose().pushMatrix();
      graphics.pose().translate(this.width / 2.0F, this.height / 2.0F);
      graphics.pose().scale(this.uiScale(), this.uiScale());
      graphics.pose().translate(-this.width / 2.0F, -this.height / 2.0F);
   }

   protected void init() {
      this.left = (this.width - W) / 2;
      this.top = (this.height - H) / 2;
      this.game = (Button)this.addRenderableWidget(
         Button.builder(Component.empty(), b -> this.send(TableActionPayload.Action.GAME_NEXT)).bounds(this.left + 10, this.top + 23, 170, 20).build()
      );
      this.target = (Button)this.addRenderableWidget(
         Button.builder(Component.empty(), b -> this.send(TableActionPayload.Action.TARGET_NEXT)).bounds(this.left + 184, this.top + 23, 106, 20).build()
      );

      for (int s = 0; s < GameState.SEATS; s++) {
         int seat = s;
         this.seatButtons[s] = (Button)this.addRenderableWidget(
            Button.builder(Component.empty(), b -> this.seatPressed(seat))
               .bounds(this.left + W - 78, this.top + SEAT_Y + s * SEAT_H, 68, 20)
               .build()
         );
      }

      this.botLevel = (Button)this.addRenderableWidget(
         Button.builder(Component.empty(), b -> this.botLevelPressed()).bounds(this.left + 10, this.top + BOT_Y, 138, 20).build()
      );
      this.botSeat = (Button)this.addRenderableWidget(
         Button.builder(Component.empty(), b -> this.botSeatPressed()).bounds(this.left + 152, this.top + BOT_Y, 138, 20).build()
      );
      this.format = (Button)this.addRenderableWidget(
         Button.builder(Component.empty(), b -> this.send(TableActionPayload.Action.FORMAT_NEXT))
            .bounds(this.left + 10, this.top + FORMAT_Y, W - 20, 20)
            .build()
      );
      this.rack = (Button)this.addRenderableWidget(
         Button.builder(PoolTable.text("screen.rack"), b -> this.send(TableActionPayload.Action.RACK)).bounds(this.left + 10, this.top + BUTTONS_Y, 90, 20).build()
      );
      this.concede = (Button)this.addRenderableWidget(
         Button.builder(PoolTable.text("screen.concede"), b -> this.send(TableActionPayload.Action.CONCEDE))
            .bounds(this.left + 105, this.top + BUTTONS_Y, 90, 20)
            .build()
      );
      this.addRenderableWidget(Button.builder(PoolTable.text("screen.done"), b -> this.onClose()).bounds(this.left + 200, this.top + BUTTONS_Y, 90, 20).build());
      this.camera = (Button)this.addRenderableWidget(Button.builder(Component.empty(), b -> {
         PoolClientConfig.setAimCamera(!PoolClientConfig.aimCamera());
         this.refresh();
      }).bounds(this.left + 10, this.top + SETTINGS_Y, 90, 20).build());
      this.shape = (Button)this.addRenderableWidget(Button.builder(Component.empty(), b -> {
         PoolClientConfig.setCubeBalls(!PoolClientConfig.cubeBalls());
         this.refresh();
      }).bounds(this.left + 105, this.top + SETTINGS_Y, 90, 20).build());
      this.guide = (Button)this.addRenderableWidget(Button.builder(Component.empty(), b -> {
         PoolConfig.Guide now = ClientAim.guide();
         PoolConfig.Guide cap = PoolConfig.guide();
         PoolConfig.Guide next = now == PoolConfig.Guide.OFF ? cap : PoolConfig.Guide.values()[now.ordinal() + 1];
         PoolClientConfig.setAimGuide(next);
         this.refresh();
      }).bounds(this.left + 200, this.top + SETTINGS_Y, 90, 20).build());
      this.refresh();
   }

   private void seatPressed(int seat) {
      PoolTableBlockEntity be = this.table();
      if (be != null) {
         this.send(
            this.me().equals(be.game.seat[seat])
               ? TableActionPayload.Action.LEAVE
               : switch (seat) {
                  case 0 -> TableActionPayload.Action.SIT_1;
                  case 1 -> TableActionPayload.Action.SIT_2;
                  case 2 -> TableActionPayload.Action.SIT_3;
                  case 3 -> TableActionPayload.Action.SIT_4;
                  default -> TableActionPayload.Action.SIT_5;
               }
         );
      }
   }

   private void botLevelPressed() {
      PoolTableBlockEntity be = this.table();
      PoolBot.Level level = be == null ? null : be.game.botLevel();
      if (level != null) {
         this.send(botAction(level.next()));
      } else {
         botChoice = botChoice.next();
         this.refresh();
      }
   }

   private void botSeatPressed() {
      PoolTableBlockEntity be = this.table();
      if (be != null) {
         this.send(be.game.hasBot() ? TableActionPayload.Action.REMOVE_BOT : botAction(botChoice));
      }
   }

   private static TableActionPayload.Action botAction(PoolBot.Level level) {
      return TableActionPayload.Action.values()[TableActionPayload.Action.BOT_EASY.ordinal() + level.ordinal()];
   }

   private void send(TableActionPayload.Action action) {
      Net.toServer(new TableActionPayload(this.pos, action));
   }

   private void refresh() {
      PoolTableBlockEntity be = this.table();
      if (be != null) {
         GameState game = be.game;
         boolean seated = game.seated(this.me());
         boolean anyPlayer = false;

         for (int s = 0; s < game.players(); s++) {
            anyPlayer |= game.player(s);
         }

         boolean mayManage = seated | !anyPlayer;
         boolean between = !be.moving() && !game.inGame();
         this.game.setMessage(PoolTable.text("screen.game", Component.translatable("pooltable.game." + game.type.key())));
         this.game.active = mayManage && between;
         this.target.visible = game.type == GameType.STRAIGHT;
         this.target.setMessage(PoolTable.text("screen.target", game.target));
         this.target.active = mayManage && between;

         for (int s = 0; s < GameState.SEATS; s++) {
            boolean mine = this.me().equals(game.seat[s]);
            this.seatButtons[s].visible = s < game.players();
            this.seatButtons[s].setMessage(PoolTable.text(mine ? "screen.leave" : "screen.sit"));
         }
         this.format.setMessage(PoolTable.text("screen.format", Component.translatable("pooltable.format." + game.format.key())));
         this.format.active = mayManage && between;

         PoolBot.Level level = game.hasBot() ? game.botLevel() : botChoice;
         this.botLevel.setMessage(PoolTable.text("screen.bot_level", Component.translatable("pooltable.bot." + level.key())));
         this.botSeat.setMessage(PoolTable.text(game.hasBot() ? "screen.bot_remove" : "screen.bot_play"));
         int mine = game.seatOf(this.me());

         for (int s = 0; s < game.players() && mine < 0; s++) {
            mine = game.seat[s] == null ? s : -1;
         }

         int free = 0;

         for (int s = 0; s < game.players(); s++) {
            free += s == mine || game.seat[s] != null && !this.me().equals(game.seat[s]) ? 0 : 1;
         }

         this.botLevel.active = !game.hasBot() || mayManage;
         this.botSeat.active = mayManage && (game.hasBot() || mine >= 0 && free > 0 && !be.moving());
         this.camera.setMessage(PoolTable.text(PoolClientConfig.aimCamera() ? "screen.camera_on" : "screen.camera_off"));
         this.shape.setMessage(PoolTable.text(PoolClientConfig.cubeBalls() ? "screen.balls_cubes" : "screen.balls_round"));
         this.guide.setMessage(PoolTable.text("screen.guide_" + ClientAim.guide().name().toLowerCase(Locale.ROOT)));
         this.guide.active = PoolConfig.guide() != PoolConfig.Guide.OFF;
         this.rack.active = !be.moving() && (game.phase == GameState.Phase.PRACTICE || seated);
         this.concede.active = !be.moving() && game.inGame() && seated;
      }
   }

   public void tick() {
      if (this.table() == null) {
         this.onClose();
      } else {
         this.refresh();
      }
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      return super.mouseClicked(this.logicalMouse(event), doubleClick);
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      return super.mouseReleased(this.logicalMouse(event));
   }

   public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
      return super.mouseDragged(this.logicalMouse(event), deltaX / this.uiScale(), deltaY / this.uiScale());
   }

   public void mouseMoved(double mouseX, double mouseY) {
      super.mouseMoved(this.logicalX(mouseX), this.logicalY(mouseY));
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      return super.mouseScrolled(this.logicalX(mouseX), this.logicalY(mouseY), scrollX, scrollY);
   }

   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      this.pushUiScale(g);
      try {
         super.extractRenderState(g, (int)this.logicalX(mouseX), (int)this.logicalY(mouseY), partialTick);
         PoolTableBlockEntity be = this.table();
         if (be != null) {
            GameState game = be.game;
            g.centeredText(this.font, this.title, this.width / 2, this.top + 7, -1);

            for (int s = 0; s < game.players(); s++) {
               int y = this.top + SEAT_Y + s * SEAT_H;
               String name = game.name[s].isEmpty() ? "-" : game.name[s];
               Component seat = game.teams()
                  ? PoolTable.text("screen.team_seat", game.teamOf(s) + 1, s + 1, name)
                  : PoolTable.text("screen.seat", s + 1, name);
               g.text(this.font, seat, this.left + 12, y + 1, game.seat[s] == null ? -7303024 : -1);
               g.text(this.font, detail(game, s), this.left + 12, y + 11, -5723992);
            }

            Component status = game.phase == GameState.Phase.PRACTICE ? PoolTable.text("screen.practice", game.players()) : PoolHud.message(game);
            g.centeredText(this.font, status, this.width / 2, this.top + STATUS_Y, -5720);

            for (int i = 0; i < 2; i++) {
               g.text(this.font, PoolTable.text("screen.help." + i), this.left + 12, this.top + HELP_Y + i * 11, -4671304);
            }
         }
      } finally {
         g.pose().popMatrix();
      }
   }

   private static Component detail(GameState game, int s) {
      if (game.type.points()) {
         return PoolTable.text("screen.points_wins", game.score[s], game.wins[s]);
      } else if (game.group[s] == EightBall.Group.NONE) {
         return PoolTable.text("screen.wins", game.wins[s]);
      } else {
         String group = game.group[s].name().toLowerCase(Locale.ROOT);
         String key = "pooltable.group." + (game.type == GameType.ENGLISH_8 ? "english." : "") + group;
         return PoolTable.text("screen.group_wins", Component.translatable(key), game.wins[s]);
      }
   }

   public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      this.pushUiScale(g);
      try {
         super.extractBackground(g, (int)this.logicalX(mouseX), (int)this.logicalY(mouseY), partialTick);
         this.panel(g);
      } finally {
         g.pose().popMatrix();
      }
   }

   private void panel(GuiGraphicsExtractor g) {
      g.fill(this.left, this.top, this.left + W, this.top + H, -535292912);
      g.outline(this.left, this.top, W, H, -9549270);
      g.fill(this.left + 6, this.top + 19, this.left + W - 6, this.top + 20, -12965348);
      g.fill(this.left + 6, this.top + HELP_Y - 5, this.left + W - 6, this.top + HELP_Y - 4, -12965348);
   }

   public boolean isPauseScreen() {
      return false;
   }
}
