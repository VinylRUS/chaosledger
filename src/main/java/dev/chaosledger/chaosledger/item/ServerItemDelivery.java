package dev.chaosledger.chaosledger.item;

import dev.chaosledger.chaosledger.reward.ItemDelivery;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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
        Identifier id = parseIdentifier(itemId);
        if (id == null) return false;
        // BuiltInRegistries.ITEM.get(id) returns Optional<Holder.Reference<Item>>;
        // unwrap to the Item, treating "not registered" as a delivery failure.
        Item item = BuiltInRegistries.ITEM.get(id).map(Holder.Reference::value).orElse(null);
        if (item == null) return false;
        ItemStack stack = new ItemStack(item, count);
        return player.getInventory().add(stack);
    }

    /**
     * Parse a {@code "namespace:path"} string into an {@link Identifier}.
     * Returns {@code null} if the string is not a valid namespaced id (so the
     * caller can fall back to a money grant). The {@code namespace} part is
     * optional and defaults to {@code minecraft}.
     */
    private static Identifier parseIdentifier(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        int colon = raw.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : raw.substring(0, colon);
        String path = colon < 0 ? raw : raw.substring(colon + 1);
        if (namespace.isEmpty() || path.isEmpty()) return null;
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
}
