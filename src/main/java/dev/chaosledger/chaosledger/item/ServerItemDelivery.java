package dev.chaosledger.chaosledger.item;

import dev.chaosledger.chaosledger.reward.ItemDelivery;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Minecraft-side implementation of {@link ItemDelivery}.
 *
 * <p>Looks up the player by UUID on the server, resolves the {@code itemId} to a
 * registered {@link Item}, and inserts the stack into the player's inventory.
 * Returns {@code false} if the inventory was full so the caller can fall back.</p>
 */
public final class ServerItemDelivery implements ItemDelivery {

    private final java.util.function.Function<UUID, ServerPlayerEntity> playerLookup;

    public ServerItemDelivery(java.util.function.Function<UUID, ServerPlayerEntity> playerLookup) {
        this.playerLookup = playerLookup;
    }

    @Override
    public boolean deliver(UUID playerUuid, String itemId, int count) {
        ServerPlayerEntity player = playerLookup.apply(playerUuid);
        if (player == null) return false;
        Item item = Registries.ITEM.get(Identifier.of(itemId));
        if (item == null) return false;
        ItemStack stack = new ItemStack(item, count);
        return player.getInventory().insertStack(stack);
    }
}
