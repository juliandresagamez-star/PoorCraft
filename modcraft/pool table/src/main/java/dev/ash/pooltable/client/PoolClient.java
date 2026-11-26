package dev.ash.pooltable.client;

import dev.ash.pooltable.PoolConfig;
import dev.ash.pooltable.PoolTable;
import dev.ash.pooltable.block.PoolTableBlockEntity;
import dev.ash.pooltable.fabric.ConfigSyncPayload;
import dev.ash.pooltable.fabric.FabricNet;
import dev.ash.pooltable.fabric.client.FabricClientShim;
import dev.ash.pooltable.net.AimSyncPayload;
import dev.ash.pooltable.net.ShotPayload;
import java.util.List;
import java.util.Objects;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents.Load;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.StartTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.BeforeBlockOutline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class PoolClient implements ClientModInitializer {
   public void onInitializeClient() {
      PoolClientEvents.setup();
      FabricNet.toServer = ClientPlayNetworking::send;
      ClientPlayNetworking.registerGlobalReceiver(ShotPayload.TYPE, (p, ctx) -> p.handle(ctx.player()));
      ClientPlayNetworking.registerGlobalReceiver(AimSyncPayload.TYPE, (p, ctx) -> p.handle(ctx.player()));
      ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.TYPE, (p, ctx) -> p.apply());
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> {
         PoolClientEvents.loggedOut();
         PoolConfig.clearSynced();
      });
      ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((Load)(be, level) -> {
         if (be instanceof PoolTableBlockEntity table) {
            table.onLoad();
         }
      });
      ConditionalItemModelProperties.ID_MAPPER.put(PoolTable.id("aiming"), AimingProperty.MAP_CODEC);
      ConditionalItemModelProperties.ID_MAPPER.put(PoolTable.id("vr"), VrHeldProperty.MAP_CODEC);
      BlockEntityRenderers.register(PoolTable.TABLE_BE.get(), TableRenderer::new);
      BlockEntityRenderers.register(PoolTable.RACK_BE.get(), CueRackRenderer::new);
      BlockEntityRenderers.register(PoolTable.LAMP_BE.get(), LampRenderer::new);
      BlockColorRegistry.register(List.of(new BlockTintSource() {
         {
            Objects.requireNonNull(PoolClient.this);
         }

         public int color(BlockState state) {
            return PoolClientEvents.clothColour(state, null, null);
         }

         public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            return PoolClientEvents.clothColour(state, level, pos);
         }
      }), new Block[]{(Block)PoolTable.TABLE.get()});
      HudElementRegistry.addLast(PoolTable.id("hud"), PoolClientEvents::hud);
      HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (g, delta) -> {
         if (!PoolClientEvents.hideCrosshair()) {
            vanilla.extractRenderState(g, delta);
         }
      });
      ClientTickEvents.START_CLIENT_TICK.register((StartTick)mc -> PoolClientEvents.tickStart());
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)mc -> PoolClientEvents.tickEnd());
      LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((BeforeBlockOutline)(context, outline) -> {
         if (PoolClientEvents.hideOutline()) {
            return false;
         } else {
            AABB box = PoolClientEvents.tableOutline(outline.pos(), Minecraft.getInstance().level.getBlockState(outline.pos()));
            if (box == null) {
               return true;
            } else {
               FabricClientShim.get().drawOutline(context, box);
               return false;
            }
         }
      });
   }
}
