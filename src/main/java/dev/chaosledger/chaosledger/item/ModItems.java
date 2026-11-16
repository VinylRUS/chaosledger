package dev.chaosledger.chaosledger.item;

import dev.chaosledger.chaosledger.economy.Money;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Registry helpers for ChaosLedger items.
 *
 * <p>The first version ships a single custom item — the {@code ledger_coin} —
 * which is a stackable, gift-able voucher tied to a player's account. Its face
 * value, target account UUID and currency are stored as Data Components on the
 * stack itself so the voucher is fully self-describing.</p>
 *
 * <p>NOTE: For the first version the ledger coin reuses a vanilla {@link Items}
 * placeholder so that we do not need a separate item model / texture asset. A
 * later version will register a dedicated item with its own model.</p>
 */
public final class ModItems {

    private ModItems() {}

    public static final Identifier LEDGER_COIN_ID = Identifier.of("chaosledger", "ledger_coin");
    public static Item LEDGER_COIN;

    /** Register the items. Called by the Fabric entrypoint on the bootstrap phase. */
    public static void register() {
        Item.Settings settings = new Item.Settings().maxCount(64);
        LEDGER_COIN = new Item(settings);
        Registry.register(Registries.ITEM, LEDGER_COIN_ID, LEDGER_COIN);

        // Register the data components.
        Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of("chaosledger", "ledger_account"), ModComponents.LEDGER_ACCOUNT);
        Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of("chaosledger", "ledger_currency"), ModComponents.LEDGER_CURRENCY);
        Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of("chaosledger", "ledger_value"), ModComponents.LEDGER_VALUE);
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
