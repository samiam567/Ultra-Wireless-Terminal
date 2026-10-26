package com.ultrawireless.terminal;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Method;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import com.tom.storagemod.components.WorldPos;
import com.tom.storagemod.item.WirelessTerminal;

public class UltraWirelessTerminalItem extends Item implements WirelessTerminal {

	public static final TagKey<net.minecraft.world.level.block.Block> REMOTE_ACTIVATE =
			BlockTags.create(ResourceLocation.fromNamespaceAndPath("toms_storage", "remote_activate"));

	private static final ResourceLocation STOCK_TICKER_LOCATION =
			ResourceLocation.fromNamespaceAndPath("create", "stock_ticker");

	public UltraWirelessTerminalItem(Properties properties) {
		super(properties);
	}

	public static boolean isStockTicker(net.minecraft.world.level.block.Block block) {
		if (block == null)
			return false;
		Optional<ResourceLocation> loc = block.builtInRegistryHolder().unwrapKey().map(key -> key.location());
		return loc.isPresent() && loc.get().equals(STOCK_TICKER_LOCATION);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext tooltipContext, List<Component> tooltip, TooltipFlag tooltipFlag) {
		List<WorldPos> linked = stack.get(UltraWirelessTerminalMod.BOUND_TERMINALS.get());
		int termCount = linked == null ? 0 : linked.size();
		boolean createPresent = isCreateInstalled();
		tooltip.add(Component.literal("Linked terminals: " + termCount).withStyle(ChatFormatting.GRAY));
		if (createPresent)
			tooltip.add(Component.literal("Linked stock tickers: " + stockTickerCount(stack)).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.ultra_wireless_terminal.open_terminal_keybind", Component.keybind("key.toms_storage.open_terminal").withStyle(ChatFormatting.GREEN)));
		if (Screen.hasShiftDown()) {
			tooltip.add(Component.literal("Links to multiple Tom's Storage terminals.").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			tooltip.add(Component.literal("Sneak + right-click a terminal to link/unlink.").withStyle(ChatFormatting.GRAY));
			if (createPresent) {
				tooltip.add(Component.literal("Links to multiple Create stock tickers.").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
				tooltip.add(Component.literal("Sneak + right-click a stock ticker to link/unlink.").withStyle(ChatFormatting.GRAY));
			}
			tooltip.add(Component.literal("Sneak + right-click in air to clear all.").withStyle(ChatFormatting.GRAY));
			tooltip.add(Component.literal("Right-click to open the closest available linked terminal.").withStyle(ChatFormatting.GRAY));
			if (createPresent)
				tooltip.add(Component.translatable("tooltip.ultra_wireless_terminal.open_stock_ticker_keybind", Component.keybind("key.ultra_wireless_terminal.open_stock_ticker").withStyle(ChatFormatting.GRAY)));
		} else {
			tooltip.add(Component.translatable("tooltip.toms_storage.hold_shift_for_info").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
		}
	}

	private static int stockTickerCount(ItemStack stack) {
		List<WorldPos> linked = stack.get(UltraWirelessTerminalMod.BOUND_STOCK_TICKERS.get());
		return linked == null ? 0 : linked.size();
	}

	private static boolean isCreateInstalled() {
		try {
			Class<?> modList = Class.forName("net.neoforged.fml.ModList");
			Object instance = modList.getMethod("get").invoke(null);
			return (boolean) modList.getMethod("isLoaded", String.class).invoke(instance, "create");
		} catch (ReflectiveOperationException e) {
			return false;
		}
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level worldIn, Player playerIn, InteractionHand handIn) {
		ItemStack stack = playerIn.getItemInHand(handIn);
		if (playerIn.isShiftKeyDown()) {
			if (!worldIn.isClientSide) {
				stack.set(UltraWirelessTerminalMod.BOUND_TERMINALS.get(), new ArrayList<>());
				playerIn.displayClientMessage(Component.literal("Unlinked all terminals"), true);
			}
			return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);
		}
		return new InteractionResultHolder<>(activateTerminal(worldIn, stack, playerIn, handIn), stack);
	}

	public static InteractionResult activateTerminal(Level worldIn, ItemStack stack, Player playerIn, InteractionHand handIn) {
		List<WorldPos> linked = stack.get(UltraWirelessTerminalMod.BOUND_TERMINALS.get());
		if (linked != null && !linked.isEmpty()) {
			if (!worldIn.isClientSide) {
				List<WorldPos> available = new ArrayList<>();
				for (WorldPos wp : linked) {
					Level termWorld = worldIn.getServer().getLevel(wp.dim());
					if (termWorld == null) continue;
					BlockPos bp = wp.pos();
					if (!termWorld.isLoaded(bp)) continue;
					BlockState state = termWorld.getBlockState(bp);
					if (!state.is(REMOTE_ACTIVATE)) continue;
					available.add(wp);
				}
				WorldPos best = selectClosest(available, playerIn.level().dimension(),
						playerIn.getX(), playerIn.getY(), playerIn.getZ());
				if (best != null) {
					int x = best.pos().getX();
					int y = best.pos().getY();
					int z = best.pos().getZ();
					Level termWorld = worldIn.getServer().getLevel(best.dim());
					BlockHitResult lookingAt = new BlockHitResult(new Vec3(x, y, z), Direction.UP, new BlockPos(x, y, z), true);
					BlockState state = termWorld.getBlockState(lookingAt.getBlockPos());
					if (state.is(REMOTE_ACTIVATE)) {
						return state.useWithoutItem(termWorld, playerIn, lookingAt);
					} else {
						playerIn.displayClientMessage(Component.translatable("chat.toms_storage.terminal_invalid_block"), true);
					}
				} else {
					playerIn.displayClientMessage(Component.translatable("chat.toms_storage.terminal_not_loaded"), true);
				}
			} else {
				return InteractionResult.CONSUME;
			}
		}
		return InteractionResult.PASS;
	}

	@Override
	public InteractionResult useOn(UseOnContext c) {
		if (c.isSecondaryUseActive()) {
			if (!c.getLevel().isClientSide) {
				BlockPos pos = c.getClickedPos();
				BlockState state = c.getLevel().getBlockState(pos);
				ItemStack stack = c.getItemInHand();
				if (state.is(REMOTE_ACTIVATE)) {
					WorldPos wp = new WorldPos(c.getLevel().dimension(), pos);
					List<WorldPos> current = stack.get(UltraWirelessTerminalMod.BOUND_TERMINALS.get());
					List<WorldPos> list = new ArrayList<>(current == null ? List.of() : current);
					if (list.contains(wp)) {
						list.remove(wp);
						if (c.getPlayer() != null)
							c.getPlayer().displayClientMessage(Component.literal("Unlinked terminal"), true);
					} else {
						list.add(wp);
						if (c.getPlayer() != null)
							c.getPlayer().displayClientMessage(Component.translatable("chat.toms_storage.terminal_bound"), true);
					}
					stack.set(UltraWirelessTerminalMod.BOUND_TERMINALS.get(), list);
					return InteractionResult.SUCCESS;
				} else if (isStockTicker(state.getBlock())) {
					WorldPos wp = new WorldPos(c.getLevel().dimension(), pos);
					List<WorldPos> current = stack.get(UltraWirelessTerminalMod.BOUND_STOCK_TICKERS.get());
					List<WorldPos> list = new ArrayList<>(current == null ? List.of() : current);
					if (list.contains(wp)) {
						list.remove(wp);
						if (c.getPlayer() != null)
							c.getPlayer().displayClientMessage(Component.literal("Unlinked stock ticker"), true);
					} else {
						list.add(wp);
						if (c.getPlayer() != null)
							c.getPlayer().displayClientMessage(Component.literal("Linked stock ticker"), true);
					}
					stack.set(UltraWirelessTerminalMod.BOUND_STOCK_TICKERS.get(), list);
					return InteractionResult.SUCCESS;
				}
			} else {
				return InteractionResult.CONSUME;
			}
		}
		return InteractionResult.PASS;
	}

	public static void activateStockTicker(ServerPlayer player, ItemStack stack) {
		if (player.level().isClientSide())
			return;
		List<WorldPos> linked = stack.get(UltraWirelessTerminalMod.BOUND_STOCK_TICKERS.get());
		if (linked == null || linked.isEmpty())
			return;
		List<WorldPos> available = new ArrayList<>();
		for (WorldPos wp : linked) {
			Level termWorld = player.getServer().getLevel(wp.dim());
			if (termWorld == null)
				continue;
			BlockPos bp = wp.pos();
			if (!termWorld.isLoaded(bp))
				continue;
			if (!isStockTicker(termWorld.getBlockState(bp).getBlock()))
				continue;
			available.add(wp);
		}
		WorldPos best = selectClosest(available, player.level().dimension(),
				player.getX(), player.getY(), player.getZ());
		if (best != null)
			openStockTickerAt(player, best.pos());
	}

	private static void openStockTickerAt(ServerPlayer player, BlockPos pos) {
		try {
			Class<?> handlerClass = Class.forName("com.simibubi.create.content.logistics.stockTicker.StockTickerInteractionHandler");
			Method method = handlerClass.getMethod("interactWithLogisticsManagerAt", Player.class, Level.class, BlockPos.class);
			method.setAccessible(true);
			method.invoke(null, player, player.level(), pos);
		} catch (ClassNotFoundException e) {
		} catch (Exception e) {
			player.displayClientMessage(Component.literal("Failed to open stock ticker: " + e.getMessage()), true);
		}
	}

	public static WorldPos selectClosest(List<WorldPos> candidates, net.minecraft.resources.ResourceKey<Level> playerDim,
			double px, double py, double pz) {
		WorldPos best = null;
		boolean bestSameDim = false;
		double bestDist = Double.MAX_VALUE;
		for (WorldPos wp : candidates) {
			boolean sameDim = wp.dim().equals(playerDim);
			double dx = wp.pos().getX() + 0.5D - px;
			double dy = wp.pos().getY() + 0.5D - py;
			double dz = wp.pos().getZ() + 0.5D - pz;
			double dist = sameDim ? (dx * dx + dy * dy + dz * dz) : Double.MAX_VALUE;
			if (best == null || (sameDim && !bestSameDim) || (sameDim == bestSameDim && dist < bestDist)) {
				best = wp;
				bestSameDim = sameDim;
				bestDist = dist;
			}
		}
		return best;
	}

	@Override
	public int getRange(Player pl, ItemStack stack) {
		return 64;
	}

	@Override
	public void open(Player sender, ItemStack t) {
		activateTerminal(sender.level(), t, sender, InteractionHand.MAIN_HAND);
	}

	@Override
	public boolean canOpen(ItemStack t) {
		return true;
	}
}
