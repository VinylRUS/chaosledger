package dev.chaosledger.chaosledger.item;

import dev.chaosledger.chaosledger.economy.Money;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Registry helpers for ChaosLedger items.
 *
 * <p>The first version ships a single custom item — the {@code ledger_coin} —
 * which is a stackable, gift-able voucher tied to a player's account. Its face
 * value, target account UUID and currency are stored as Data Components on the
 * stack itself so the voucher is fully self-describing.</p>
 *
 * <p>NOTE: For the first version the ledger coin reuses a vanilla placeholder
 * model so that we do not need a separate item model / texture asset. A later
 * version will register a dedicated item with its own model.</p>
 */
public final class ModItems {

    private ModItems() {}

    public static final Identifier LEDGER_COIN_ID = Identifier.fromNamespaceAndPath("chaosledger", "ledger_coin");
    public static final ResourceKey<Item> LEDGER_COIN_KEY =
            ResourceKey.create(Registries.ITEM, LEDGER_COIN_ID);
    public static Item LEDGER_COIN;

    /** Register the items. Called by the Fabric entrypoint on the bootstrap phase. */
    public static void register() {
        // 26.3 / 1.21.2+: the Item constructor reads the id off Item.Properties
        // (effectiveDescriptionId -> itemIdOrThrow) and NPEs at runtime if it
        // was not set via setId(ResourceKey<Item>) BEFORE construction. Setting
        // it through Registry.register(...) after the fact is too late.
        Item.Properties properties = new Item.Properties()
                .stacksTo(64)
                .setId(LEDGER_COIN_KEY);
        LEDGER_COIN = new Item(properties);
        Registry.register(BuiltInRegistries.ITEM, LEDGER_COIN_KEY, LEDGER_COIN);

        // Register the data components.
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath("chaosledger", "ledger_account"), ModComponents.LEDGER_ACCOUNT);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath("chaosledger", "ledger_currency"), ModComponents.LEDGER_CURRENCY);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath("chaosledger", "ledger_value"), ModComponents.LEDGER_VALUE);
    }

    /** Build a ledger coin stack bound to {@code playerUuid} carrying {@code value}. */
    public static ItemStack ledgerCoin(UUID playerUuid, Money value) {
        ItemStack stack = new ItemStack(LEDGER_COIN);
        stack.set(ModComponents.LEDGER_ACCOUNT, playerUuid.toString());
        stack.set(ModComponents.LEDGER_CURRENCY, value.currency());
        stack.set(ModComponents.LEDGER_VALUE, value.amount());
        return stack;
    }
}
