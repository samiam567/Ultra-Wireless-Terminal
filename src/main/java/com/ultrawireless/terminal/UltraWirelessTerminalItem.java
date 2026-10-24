package com.ultrawireless.terminal;

import java.util.ArrayList;
import java.util.List;

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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import com.tom.storagemod.components.WorldPos;
import com.tom.storagemod.item.WirelessTerminal;

public class UltraWirelessTerminalItem extends Item implements WirelessTerminal {

	public static final TagKey<net.minecraft.world.level.block.Block> REMOTE_ACTIVATE =
			BlockTags.create(ResourceLocation.fromNamespaceAndPath("toms_storage", "remote_activate"));

	public UltraWirelessTerminalItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext tooltipContext, List<Component> tooltip, TooltipFlag tooltipFlag) {
		List<WorldPos> linked = stack.get(UltraWirelessTerminalMod.BOUND_TERMINALS.get());
		int count = linked == null ? 0 : linked.size();
		tooltip.add(Component.literal("Linked terminals: " + count).withStyle(ChatFormatting.GRAY));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level worldIn, Player playerIn, InteractionHand handIn) {
		return new InteractionResultHolder<>(activateTerminal(worldIn, playerIn.getItemInHand(handIn), playerIn, handIn), playerIn.getItemInHand(handIn));
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
				if (state.is(REMOTE_ACTIVATE)) {
					ItemStack stack = c.getItemInHand();
					WorldPos wp = new WorldPos(c.getLevel().dimension(), pos);
					List<WorldPos> list = new ArrayList<>(stack.get(UltraWirelessTerminalMod.BOUND_TERMINALS.get()) == null ? List.of() : stack.get(UltraWirelessTerminalMod.BOUND_TERMINALS.get()));
					if (!list.contains(wp)) list.add(wp);
					stack.set(UltraWirelessTerminalMod.BOUND_TERMINALS.get(), list);
					if (c.getPlayer() != null)
						c.getPlayer().displayClientMessage(Component.translatable("chat.toms_storage.terminal_bound"), true);
					return InteractionResult.SUCCESS;
				}
			} else {
				return InteractionResult.CONSUME;
			}
		}
		return InteractionResult.PASS;
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
