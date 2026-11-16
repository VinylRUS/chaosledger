package dev.chaosledger.chaosledger.item;

import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;

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
 * <p>NOTE: The exact name of {@code ComponentType.Builder} factory methods may
 * differ in the 26.3 mappings. This is a best-effort first version; verify
 * against the Yarn build for 26.3 if the build fails.</p>
 */
public final class ModComponents {

    private ModComponents() {}

    public static final ComponentType<String> LEDGER_ACCOUNT =
            ComponentType.<String>builder().codec(PacketCodecs.STRING).build();

    public static final ComponentType<String> LEDGER_CURRENCY =
            ComponentType.<String>builder().codec(PacketCodecs.STRING).build();

    public static final ComponentType<Double> LEDGER_VALUE =
            ComponentType.<Double>builder().codec(PacketCodecs.DOUBLE).build();
}
