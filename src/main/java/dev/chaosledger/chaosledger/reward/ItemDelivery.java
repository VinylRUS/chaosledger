package dev.chaosledger.chaosledger.reward;

import java.util.UUID;

/**
 * Adapter used by {@link RewardEngine} to deliver in-game items to a player.
 *
 * <p>The adapter is implemented by the Fabric/Minecraft side of the mod and
 * registered at startup; it lets the engine stay Minecraft-free and testable
 * in plain Java. Implementations must be safe to call from the server thread.</p>
 */
public interface ItemDelivery {
    /**
     * Attempt to deliver {@code count} of {@code itemId} to the player.
     *
     * @return {@code true} if the items were placed into the player's inventory,
     *         {@code false} if the inventory was full (caller decides what to do)
     */
    boolean deliver(UUID playerUuid, String itemId, int count);
}
