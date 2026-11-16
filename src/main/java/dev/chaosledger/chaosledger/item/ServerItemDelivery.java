package dev.chaosledger.chaosledger.item;

import dev.chaosledger.chaosledger.reward.ItemDelivery;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;
import java.util.function.Function;

/**
 * Minecraft-side implementation of {@link ItemDelivery}.
 *
 * <p>Looks up the player by UUID on the server, resolves the {@code itemId} to a
 * registered {@link Item}, and inserts the stack into the player's inventory.
 * Returns {@code false} if the inventory was full so the caller can fall back.</p>
 */
public final class ServerItemDelivery implements ItemDelivery {

    private final Function<UUID, ServerPlayer> playerLookup;

    public ServerItemDelivery(Function<UUID, ServerPlayer> playerLookup) {
        this.playerLookup = playerLookup;
    }

    @Override
    public boolean deliver(UUID playerUuid, String itemId, int count) {
        ServerPlayer player = playerLookup.apply(playerUuid);
        if (player == null) return false;
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        if (item == null) return false;
        ItemStack stack = new ItemStack(item, count);
        return player.getInventory().add(stack);
    }
}
