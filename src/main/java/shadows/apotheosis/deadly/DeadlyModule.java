package shadows.apotheosis.deadly;

import java.io.File;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableSet;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.Color;
import net.minecraft.world.gen.feature.Feature;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegistryEvent.Register;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import shadows.apotheosis.Apotheosis;
import shadows.apotheosis.Apotheosis.ApotheosisConstruction;
import shadows.apotheosis.Apotheosis.ApotheosisReloadEvent;
import shadows.apotheosis.ApotheosisObjects;
import shadows.apotheosis.deadly.affix.AffixEvents;
import shadows.apotheosis.deadly.affix.LootRarity;
import shadows.apotheosis.deadly.affix.recipe.AffixShardingRecipe;
import shadows.apotheosis.deadly.config.DeadlyConfig;
import shadows.apotheosis.deadly.gen.BossDungeonFeature;
import shadows.apotheosis.deadly.gen.BossDungeonFeature2;
import shadows.apotheosis.deadly.gen.RogueSpawnerFeature;
import shadows.apotheosis.deadly.gen.TomeTowerFeature;
import shadows.apotheosis.deadly.gen.TroveFeature;
import shadows.apotheosis.deadly.objects.AffixTomeItem;
import shadows.apotheosis.deadly.objects.BossSpawnerBlock;
import shadows.apotheosis.deadly.objects.BossSpawnerBlock.BossSpawnerTile;
import shadows.apotheosis.deadly.objects.BossSummonerItem;
import shadows.apotheosis.deadly.objects.RarityShardItem;
import shadows.apotheosis.deadly.reload.AffixLootManager;
import shadows.apotheosis.deadly.reload.BossArmorManager;
import shadows.apotheosis.deadly.reload.BossItemManager;
import shadows.apotheosis.deadly.reload.RandomSpawnerManager;
import shadows.apotheosis.util.NameHelper;
import shadows.placebo.config.Configuration;
import shadows.placebo.recipe.RecipeHelper;

public class DeadlyModule {

	public static final Logger LOGGER = LogManager.getLogger("Apotheosis : Deadly");

	public static final EnumMap<LootRarity, RarityShardItem> RARITY_SHARDS = new EnumMap<>(LootRarity.class);
	public static final EnumMap<LootRarity, AffixTomeItem> RARITY_TOMES = new EnumMap<>(LootRarity.class);

	@SubscribeEvent
	public void preInit(ApotheosisConstruction e) {
		MinecraftForge.EVENT_BUS.register(new AffixEvents());
		MinecraftForge.EVENT_BUS.addListener(this::reloads);
		MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, DeadlyWorldGen::onBiomeLoad);
		MinecraftForge.EVENT_BUS.addListener(this::reload);
	}

	@SubscribeEvent
	public void init(FMLCommonSetupEvent e) {
		this.reload(null);
		DeadlyLoot.init();
		LootRarity[] vals = LootRarity.values();
		for (int i = 0; i < vals.length - 1; i++) {
			RecipeHelper.addRecipe(new AffixShardingRecipe(new ResourceLocation(Apotheosis.MODID, "affix_sharding_" + vals[i].name().toLowerCase(Locale.ROOT)), vals[i]));
			Apotheosis.HELPER.addShapeless(new ItemStack(RARITY_SHARDS.get(vals[i]), 2), new ItemStack(RARITY_SHARDS.get(vals[i + 1])));
		}
		RecipeHelper.addRecipe(new AffixShardingRecipe(new ResourceLocation(Apotheosis.MODID, "affix_sharding_" + LootRarity.ANCIENT.name().toLowerCase(Locale.ROOT)), LootRarity.ANCIENT));
		Color.NAMED_COLORS = new HashMap<>(Color.NAMED_COLORS);
		Color.NAMED_COLORS.put("rainbow", DeadlyModuleClient.getRainbowColor());
	}

	@SubscribeEvent
	public void register(Register<Feature<?>> e) {
		e.getRegistry().register(BossDungeonFeature.INSTANCE.setRegistryName("boss_dungeon"));
		e.getRegistry().register(BossDungeonFeature2.INSTANCE.setRegistryName("boss_dungeon_2"));
		e.getRegistry().register(RogueSpawnerFeature.INSTANCE.setRegistryName("rogue_spawner"));
		e.getRegistry().register(TroveFeature.INSTANCE.setRegistryName("trove"));
		e.getRegistry().register(TomeTowerFeature.INSTANCE.setRegistryName("tome_tower"));
	}

	@SubscribeEvent
	public void items(Register<Item> e) {
		e.getRegistry().register(new BossSummonerItem(new Item.Properties().stacksTo(1).tab(Apotheosis.APOTH_GROUP)).setRegistryName("boss_summoner"));
		for (LootRarity r : LootRarity.values()) {
			RarityShardItem shard = new RarityShardItem(r, new Item.Properties().tab(Apotheosis.APOTH_GROUP));
			shard.setRegistryName(r.name().toLowerCase(Locale.ROOT) + "_shard");
			e.getRegistry().register(shard);
			RARITY_SHARDS.put(r, shard);
		}
		for (LootRarity r : LootRarity.values()) {
			AffixTomeItem tome = new AffixTomeItem(r, new Item.Properties().tab(Apotheosis.APOTH_GROUP));
			tome.setRegistryName(r.name().toLowerCase(Locale.ROOT) + "_tome");
			e.getRegistry().register(tome);
			RARITY_TOMES.put(r, tome);
		}
	}

	@SubscribeEvent
	public void blocks(Register<Block> e) {
		e.getRegistry().register(new BossSpawnerBlock(AbstractBlock.Properties.of(Material.STONE).strength(-1).noDrops().noOcclusion()).setRegistryName("boss_spawner"));
	}

	@SubscribeEvent
	public void tiles(Register<TileEntityType<?>> e) {
		e.getRegistry().register(new TileEntityType<>(BossSpawnerTile::new, ImmutableSet.of(ApotheosisObjects.BOSS_SPAWNER), null).setRegistryName("boss_spawn_tile"));
	}

	@SubscribeEvent
	public void serializers(Register<IRecipeSerializer<?>> e) {
		e.getRegistry().register(AffixShardingRecipe.SERIALIZER.setRegistryName(new ResourceLocation(Apotheosis.MODID, "affix_sharding")));
	}

	@SubscribeEvent
	public void client(FMLClientSetupEvent e) {
		e.enqueueWork(DeadlyModuleClient::init);
	}

	public void reloads(AddReloadListenerEvent e) {
		e.addListener(AffixLootManager.INSTANCE);
		e.addListener(BossArmorManager.INSTANCE);
		e.addListener(BossItemManager.INSTANCE);
		e.addListener(RandomSpawnerManager.INSTANCE);
	}

	/**
	 * Loads all configurable data for the deadly module.
	 */
	public void reload(ApotheosisReloadEvent e) {
		DeadlyConfig.config = new Configuration(new File(Apotheosis.configDir, "deadly.cfg"));
		Configuration nameConfig = new Configuration(new File(Apotheosis.configDir, "names.cfg"));
		DeadlyConfig.loadConfigs();
		NameHelper.load(nameConfig);
		if (e == null && DeadlyConfig.config.hasChanged()) DeadlyConfig.config.save();
		if (e == null && nameConfig.hasChanged()) nameConfig.save();
	}

	public static final boolean DEBUG = false;

	public static void debugLog(BlockPos pos, String name) {
		if (DEBUG) DeadlyModule.LOGGER.info("Generated a {} at {} {} {}", name, pos.getX(), pos.getY(), pos.getZ());
	}

}
