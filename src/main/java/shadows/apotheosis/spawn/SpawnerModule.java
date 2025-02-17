package shadows.apotheosis.spawn;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableSet;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Hand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent.Register;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import shadows.apotheosis.Apotheosis;
import shadows.apotheosis.Apotheosis.ApotheosisReloadEvent;
import shadows.apotheosis.ApotheosisObjects;
import shadows.apotheosis.spawn.enchantment.CapturingEnchant;
import shadows.apotheosis.spawn.modifiers.ModifierSync;
import shadows.apotheosis.spawn.modifiers.SpawnerModifier;
import shadows.apotheosis.spawn.spawner.ApothSpawnerBlock;
import shadows.apotheosis.spawn.spawner.ApothSpawnerTile;
import shadows.placebo.config.Configuration;
import shadows.placebo.util.PlaceboUtil;

public class SpawnerModule {

	public static final Logger LOG = LogManager.getLogger("Apotheosis : Spawner");
	public static int spawnerSilkLevel = 1;
	public static int spawnerSilkDamage = 100;

	@SubscribeEvent
	public void setup(FMLCommonSetupEvent e) {
		TileEntityType.MOB_SPAWNER.factory = ApothSpawnerTile::new;
		TileEntityType.MOB_SPAWNER.validBlocks = ImmutableSet.of(Blocks.SPAWNER);
		MinecraftForge.EVENT_BUS.addListener(this::handleCapturing);
		MinecraftForge.EVENT_BUS.addListener(this::handleUseItem);
		MinecraftForge.EVENT_BUS.addListener(this::reload);
		SpawnerModifiers.registerModifiers();
		this.reload(null);
		ObfuscationReflectionHelper.setPrivateValue(Item.class, Items.SPAWNER, ItemGroup.TAB_MISC, "field_77701_a");
	}

	@SubscribeEvent
	public void blocks(Register<Block> e) {
		PlaceboUtil.registerOverride(new ApothSpawnerBlock(), Apotheosis.MODID);
	}

	@SubscribeEvent
	public void serializers(Register<IRecipeSerializer<?>> e) {
		e.getRegistry().register(ModifierSync.SERIALIZER.setRegistryName("modifiers"));
	}

	@SubscribeEvent
	public void enchants(Register<Enchantment> e) {
		e.getRegistry().register(new CapturingEnchant().setRegistryName(Apotheosis.MODID, "capturing"));
	}

	public void handleCapturing(LivingDropsEvent e) {
		Entity killer = e.getSource().getEntity();
		if (killer instanceof LivingEntity) {
			int level = EnchantmentHelper.getItemEnchantmentLevel(ApotheosisObjects.CAPTURING, ((LivingEntity) killer).getMainHandItem());
			LivingEntity killed = e.getEntityLiving();
			if (SpawnerModifiers.EGG.bannedMobs.contains(killed.getType().getRegistryName().toString())) return;
			if (killed.level.random.nextFloat() < level / 250F) {
				ItemStack egg = new ItemStack(SpawnEggItem.BY_ID.get(killed.getType()));
				e.getDrops().add(new ItemEntity(killed.level, killed.getX(), killed.getY(), killed.getZ(), egg));
			}
		}
	}

	public void handleUseItem(RightClickBlock e) {
		TileEntity te;
		if ((te = e.getWorld().getBlockEntity(e.getPos())) instanceof ApothSpawnerTile) {
			ItemStack s = e.getItemStack();

			if (e.getPlayer().isShiftKeyDown() && s.getItem() instanceof SpawnEggItem) {
				if (SpawnerModifiers.EGG.modify((ApothSpawnerTile) te, s, false)) {
					e.setCanceled(true);
					return;
				}
			}

			boolean inverse = SpawnerModifiers.INVERSE.getIngredient().test(e.getPlayer().getItemInHand(e.getHand() == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND));
			for (SpawnerModifier sm : SpawnerModifiers.MODIFIERS.values())
				if (sm.canModify((ApothSpawnerTile) te, s, inverse)) e.setUseBlock(Result.ALLOW);
		}
	}

	public void reload(ApotheosisReloadEvent e) {
		Configuration config = new Configuration(new File(Apotheosis.configDir, "spawner.cfg"));
		spawnerSilkLevel = config.getInt("Spawner Silk Level", "general", 1, -1, 127, "The level of silk touch needed to harvest a spawner.  Set to -1 to disable, 0 to always drop.  The enchantment module can increase the max level of silk touch.");
		spawnerSilkDamage = config.getInt("Spawner Silk Damage", "general", 100, 0, 100000, "The durability damage dealt to an item that silk touches a spawner.");
		SpawnerModifiers.reload(config);
		if (e == null && config.hasChanged()) config.save();
	}

}