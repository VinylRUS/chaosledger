package dev.chaosledger.chaosledger.item;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.StreamCodecs;

/**
 * Data-component types registered by ChaosLedger.
 *
 * <p>Components are the canonical way to attach structured data to an item
 * stack in Minecraft 26.3. The Ledger Item carries:</p>
 * <ul>
 *   <li>{@code ledger_account} — UUID of the account this item is bound to.</li>
 *   <li>{@code ledger_currency} — currency code the item is redeemable in.</li>
 *   <li>{@code ledger_value} — stored face value in the given currency.</li>
 * </ul>
 *
 * <p>The builder uses Mojang official mappings (mojmap): {@link DataComponentType}
 * + {@link StreamCodecs}. If the 26.3 build still rejects a symbol, the most
 * likely drift is in {@code StreamCodecs.UTF_8} vs {@code StreamCodecs.STRING}
 * — try the other one and rebuild.</p>
 */
public final class ModComponents {

    private ModComponents() {}

    public static final DataComponentType<String> LEDGER_ACCOUNT =
            DataComponentType.<String>builder().streamCodec(StreamCodecs.UTF_8).build();

    public static final DataComponentType<String> LEDGER_CURRENCY =
            DataComponentType.<String>builder().streamCodec(StreamCodecs.UTF_8).build();

    public static final DataComponentType<Double> LEDGER_VALUE =
            DataComponentType.<Double>builder().streamCodec(StreamCodecs.DOUBLE).build();
}
