package dev.chaosledger.chaosledger.reward;

import dev.chaosledger.chaosledger.economy.Money;

import java.util.Objects;

/**
 * A reward definition that can be granted to a player.
 *
 * <p>A reward is intentionally simple for the first version: it is either a
 * {@link Type#MONEY} amount or a {@link Type#ITEM} grant. Future versions can
 * extend {@link Type} with {@code EXPERIENCE}, {@code PERMISSION}, {@code COMMAND}
 * etc. The reward is serialised to JSON for storage and audit.</p>
 */
public record Reward(Type type, Money money, String itemId, int count, String label) {

    public enum Type { MONEY, ITEM }

    public Reward {
        Objects.requireNonNull(type, "type");
        label = label == null ? defaultLabel(type) : label;
        switch (type) {
            case MONEY -> {
                if (money == null) {
                    throw new IllegalArgumentException("MONEY reward requires a Money value");
                }
            }
            case ITEM -> {
                if (itemId == null || itemId.isBlank()) {
                    throw new IllegalArgumentException("ITEM reward requires an itemId");
                }
                if (count <= 0) {
                    throw new IllegalArgumentException("ITEM reward count must be > 0");
                }
            }
        }
    }

    public static Reward money(Money money) {
        return new Reward(Type.MONEY, money, null, 0, null);
    }

    public static Reward item(String itemId, int count) {
        return new Reward(Type.ITEM, null, itemId, count, null);
    }

    private static String defaultLabel(Type type) {
        return switch (type) {
            case MONEY -> "Money reward";
            case ITEM  -> "Item reward";
        };
    }
}
