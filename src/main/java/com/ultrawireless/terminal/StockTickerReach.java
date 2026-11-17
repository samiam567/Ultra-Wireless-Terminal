package com.ultrawireless.terminal;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Grants a player enough block-interaction reach that Create's stock-ticker menu survives
 * {@code MenuBase.stillValid -> SmartBlockEntity.canPlayerUse -> player.canInteractWithBlock
 * (pos, 8)} from a distance. Revoked the instant the stock menu closes, so it never leaks
 * into other interactions.
 *
 * <p>Why an attribute buff and not a mixin: {@code BLOCK_INTERACTION_RANGE} is a public
 * vanilla attribute reached through direct (compile-checked) API calls, so NeoForge's
 * production-remapper fixes it up automatically. The earlier mixin approach was obfuscation
 * fragile — its injected callback referenced mojmap {@code Player}/{@code BlockEntity}
 * descriptors that {@code remap = false} cannot rewrite, so it silently no-op'd in a
 * packaged jar and Create closed the GUI on the very first tick.
 *
 * <p>Vanilla clamps {@code BLOCK_INTERACTION_RANGE} at {@value #MAX_REACH} (the
 * {@code RangedAttribute}'s max), and Create's check is {@code reach + 8}, so the real
 * stock-ticker GUI stays open up to roughly {@value #EFFECTIVE_REACH} blocks away. Beyond
 * that (or when the ticker's chunk is unloaded — Create's menu resolves the block entity
 * on the client, so the client must be able to render it anyway), the terminal falls back
 * to its own distance-free summary screen.
 */
public final class StockTickerReach {

    /** Vanilla attribute clamp for {@code player.block_interaction_range}. */
    public static final double MAX_REACH = 64.0;
    /** Additive amount that pushes the reach attribute to the vanilla maximum. */
    public static final double BUFF_AMOUNT = MAX_REACH - 4.5;
    /** Effective reach for the stock GUI: attribute max plus Create's own 8-block margin. */
    public static final int EFFECTIVE_REACH = (int) (MAX_REACH + 8.0);

    public static final ResourceLocation MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            UltraWirelessTerminalMod.MODID, "stock_ticker_reach");

    private static final AttributeModifier MODIFIER = new AttributeModifier(
            MODIFIER_ID, BUFF_AMOUNT, AttributeModifier.Operation.ADD_VALUE);

    private StockTickerReach() {}

    /**
     * Transient reach buff. Non-permanent so it is dropped automatically on death, and
     * idempotent (re-adding by id replaces rather than stacks). Safe to call repeatedly
     * while the stock menu is open.
     */
    public static void grant(ServerPlayer player) {
        if (player == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (instance != null) {
            instance.addTransientModifier(MODIFIER);
        }
    }

    /** Revoke the buff. Idempotent; safe even when never granted. */
    public static void revoke(ServerPlayer player) {
        if (player == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (instance != null) {
            instance.removeModifier(MODIFIER_ID);
        }
    }
}
