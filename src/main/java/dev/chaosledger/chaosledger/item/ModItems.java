package dev.chaosledger.chaosledger.item;

import dev.chaosledger.chaosledger.economy.Money;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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

    public static final ResourceLocation LEDGER_COIN_ID = ResourceLocation.parse("chaosledger:ledger_coin");
    public static Item LEDGER_COIN;

    /** Register the items. Called by the Fabric entrypoint on the bootstrap phase. */
    public static void register() {
        Item.Properties properties = new Item.Properties().stacksTo(64);
        LEDGER_COIN = new Item(properties);
        Registry.register(BuiltInRegistries.ITEM, LEDGER_COIN_ID, LEDGER_COIN);

        // Register the data components.
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                ResourceLocation.parse("chaosledger:ledger_account"), ModComponents.LEDGER_ACCOUNT);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                ResourceLocation.parse("chaosledger:ledger_currency"), ModComponents.LEDGER_CURRENCY);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                ResourceLocation.parse("chaosledger:ledger_value"), ModComponents.LEDGER_VALUE);
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
