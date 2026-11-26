package dev.ash.pooltable;

import dev.ash.pooltable.block.CueRackBlockEntity;
import dev.ash.pooltable.block.FloorCueRackBlock;
import dev.ash.pooltable.block.LampKind;
import dev.ash.pooltable.block.PoolLampBlock;
import dev.ash.pooltable.block.PoolLampBlockEntity;
import dev.ash.pooltable.block.PoolTableBlock;
import dev.ash.pooltable.block.PoolTableBlockEntity;
import dev.ash.pooltable.block.TableWood;
import dev.ash.pooltable.block.WallCueRackBlock;
import dev.ash.pooltable.fabric.ConfigSyncPayload;
import dev.ash.pooltable.fabric.FabricNet;
import dev.ash.pooltable.fabric.Ref;
import dev.ash.pooltable.item.CueItem;
import dev.ash.pooltable.item.CueRackItem;
import dev.ash.pooltable.item.PoolLampItem;
import dev.ash.pooltable.item.PoolTableItem;
import dev.ash.pooltable.net.AimPayload;
import dev.ash.pooltable.net.AimSyncPayload;
import dev.ash.pooltable.net.Net;
import dev.ash.pooltable.net.PlaceCuePayload;
import dev.ash.pooltable.net.ShootPayload;
import dev.ash.pooltable.net.ShotPayload;
import dev.ash.pooltable.net.TableActionPayload;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.ModifyOutput;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents.Load;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarting;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public class PoolTable implements ModInitializer {
   public static final String MOD_ID = "pooltable";
   public static final Ref<PoolTableBlock> TABLE = new Ref<>();
   public static final Map<TableWood, Ref<PoolTableItem>> TABLE_ITEMS = new EnumMap<>(TableWood.class);
   public static final Ref<PoolTableItem> TABLE_ITEM;
   public static final Ref<CueItem> CUE;
   public static final Ref<WallCueRackBlock> WALL_RACK;
   public static final Ref<FloorCueRackBlock> FLOOR_RACK;
   public static final Ref<CueRackItem> WALL_RACK_ITEM;
   public static final Ref<CueRackItem> FLOOR_RACK_ITEM;
   public static final Ref<BlockEntityType<PoolTableBlockEntity>> TABLE_BE;
   public static final Ref<BlockEntityType<CueRackBlockEntity>> RACK_BE;
   public static final Ref<BlockEntityType<PoolLampBlockEntity>> LAMP_BE;
   public static final Map<LampKind, Ref<PoolLampBlock>> LAMPS;
   public static final Map<LampKind, Ref<PoolLampItem>> LAMP_ITEMS;
   public static final Ref<SoundEvent> BALL_HIT;
   public static final Ref<SoundEvent> CUSHION;
   public static final Ref<SoundEvent> POCKET;
   public static final Ref<SoundEvent> CUE_HIT;
   public static final Ref<SoundEvent> RACK;
   public static ClientHooks clientHooks;

   public static Identifier id(String path) {
      return Identifier.fromNamespaceAndPath("pooltable", path);
   }

   public static MutableComponent text(String key, Object... args) {
      return Component.translatable("pooltable." + key, args);
   }

   private static <T> T register(Registry<? super T> registry, String name, T thing) {
      Registry.register(registry, id(name), thing);
      return thing;
   }

   private static <T extends Block> T block(String name, Function<Properties, T> make, Properties properties) {
      ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(name));
      return (T)Registry.register(BuiltInRegistries.BLOCK, key, make.apply(properties.setId(key)));
   }

   private static <T extends Item> T item(
      String name, Function<net.minecraft.world.item.Item.Properties, T> make, net.minecraft.world.item.Item.Properties properties
   ) {
      ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
      return (T)Registry.register(BuiltInRegistries.ITEM, key, make.apply(properties.setId(key)));
   }

   public void onInitialize() {
      VersionGate.check();
      TABLE.set(block("pool_table", PoolTableBlock::new, PoolContent.table()));

      for (TableWood wood : TableWood.values()) {
         TABLE_ITEMS.get(wood)
            .set(item(wood.item, p -> new PoolTableItem((Block)TABLE.get(), wood, p), new net.minecraft.world.item.Item.Properties().stacksTo(1)));
      }

      CUE.set(item("cue", CueItem::new, new net.minecraft.world.item.Item.Properties().stacksTo(1)));
      WALL_RACK.set(block("wall_cue_rack", WallCueRackBlock::new, PoolContent.rack()));
      FLOOR_RACK.set(block("floor_cue_rack", FloorCueRackBlock::new, PoolContent.rack()));
      WALL_RACK_ITEM.set(
         item("wall_cue_rack", p -> new CueRackItem((Block)WALL_RACK.get(), p), new net.minecraft.world.item.Item.Properties().useBlockDescriptionPrefix())
      );
      FLOOR_RACK_ITEM.set(
         item("floor_cue_rack", p -> new CueRackItem((Block)FLOOR_RACK.get(), p), new net.minecraft.world.item.Item.Properties().useBlockDescriptionPrefix())
      );
      TABLE_BE.set(
         register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            "pool_table",
            FabricBlockEntityTypeBuilder.create(PoolTableBlockEntity::new, new Block[]{(Block)TABLE.get()}).build()
         )
      );
      RACK_BE.set(
         register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            "cue_rack",
            FabricBlockEntityTypeBuilder.create(CueRackBlockEntity::new, new Block[]{(Block)WALL_RACK.get(), (Block)FLOOR_RACK.get()}).build()
         )
      );

      for (LampKind kind : LampKind.values()) {
         PoolLampBlock lamp = LAMPS.get(kind).set(block(kind.id, p -> new PoolLampBlock(kind, p), PoolContent.lamp(kind)));
         LAMP_ITEMS.get(kind).set(item(kind.id, p -> new PoolLampItem(lamp, p), new net.minecraft.world.item.Item.Properties().useBlockDescriptionPrefix()));
      }

      LAMP_BE.set(
         register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            "lamp",
            FabricBlockEntityTypeBuilder.create(PoolLampBlockEntity::new, LAMPS.values().stream().map(Ref::get).toArray(Block[]::new)).build()
         )
      );
      BALL_HIT.set(sound("ball_hit"));
      CUSHION.set(sound("cushion"));
      POCKET.set(sound("pocket"));
      CUE_HIT.set(sound("cue_hit"));
      RACK.set(sound("rack"));
      CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register((ModifyOutput)entries -> {
         TABLE_ITEMS.values().forEach(table -> entries.accept((ItemLike)table.get()));
         entries.accept((ItemLike)CUE.get());
         entries.accept((ItemLike)WALL_RACK_ITEM.get());
         entries.accept((ItemLike)FLOOR_RACK_ITEM.get());
         LAMP_ITEMS.values().forEach(lampx -> entries.accept((ItemLike)lampx.get()));
      });
      CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register((ModifyOutput)entries -> entries.accept((ItemLike)CUE.get()));
      PayloadTypeRegistry.serverboundPlay().register(ShootPayload.TYPE, ShootPayload.STREAM_CODEC);
      PayloadTypeRegistry.serverboundPlay().register(PlaceCuePayload.TYPE, PlaceCuePayload.STREAM_CODEC);
      PayloadTypeRegistry.serverboundPlay().register(AimPayload.TYPE, AimPayload.STREAM_CODEC);
      PayloadTypeRegistry.serverboundPlay().register(TableActionPayload.TYPE, TableActionPayload.STREAM_CODEC);
      PayloadTypeRegistry.clientboundPlay().register(ShotPayload.TYPE, ShotPayload.STREAM_CODEC);
      PayloadTypeRegistry.clientboundPlay().register(AimSyncPayload.TYPE, AimSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.clientboundPlay().register(ConfigSyncPayload.TYPE, ConfigSyncPayload.STREAM_CODEC);
      ServerPlayNetworking.registerGlobalReceiver(ShootPayload.TYPE, (p, ctx) -> p.handle(ctx.player()));
      ServerPlayNetworking.registerGlobalReceiver(PlaceCuePayload.TYPE, (p, ctx) -> p.handle(ctx.player()));
      ServerPlayNetworking.registerGlobalReceiver(AimPayload.TYPE, (p, ctx) -> p.handle(ctx.player()));
      ServerPlayNetworking.registerGlobalReceiver(TableActionPayload.TYPE, (p, ctx) -> p.handle(ctx.player()));
      Net.install(new FabricNet());
      ServerLifecycleEvents.SERVER_STARTING.register((ServerStarting)server -> PoolConfig.load());
      ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, server) -> sender.sendPacket(ConfigSyncPayload.current()));
      ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((Load)(be, level) -> {
         if (be instanceof PoolTableBlockEntity table) {
            table.onLoad();
         }
      });
   }

   private static SoundEvent sound(String name) {
      return register(BuiltInRegistries.SOUND_EVENT, name, SoundEvent.createVariableRangeEvent(id(name)));
   }

   static {
      for (TableWood wood : TableWood.values()) {
         TABLE_ITEMS.put(wood, new Ref<>());
      }

      TABLE_ITEM = TABLE_ITEMS.get(TableWood.MAHOGANY);
      CUE = new Ref<>();
      WALL_RACK = new Ref<>();
      FLOOR_RACK = new Ref<>();
      WALL_RACK_ITEM = new Ref<>();
      FLOOR_RACK_ITEM = new Ref<>();
      TABLE_BE = new Ref<>();
      RACK_BE = new Ref<>();
      LAMP_BE = new Ref<>();
      LAMPS = new EnumMap<>(LampKind.class);
      LAMP_ITEMS = new EnumMap<>(LampKind.class);

      for (LampKind kind : LampKind.values()) {
         LAMPS.put(kind, new Ref<>());
         LAMP_ITEMS.put(kind, new Ref<>());
      }

      BALL_HIT = new Ref<>();
      CUSHION = new Ref<>();
      POCKET = new Ref<>();
      CUE_HIT = new Ref<>();
      RACK = new Ref<>();
      clientHooks = new ClientHooks() {};
   }
}
