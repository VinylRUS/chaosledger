package dev.chaosledger.chaosledger.item;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;

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
 * <p>On 26.3 the {@link DataComponentType.Builder} API takes a persistent
 * {@link Codec} (for save/NBT serialisation) via {@code .persistent(...)} and a
 * {@code StreamCodec} (for network sync) via {@code .networkSynchronized(...)}.
 * String network codec is {@link ByteBufCodecs#STRING_UTF8}; the double codec is
 * {@link ByteBufCodecs#DOUBLE}.</p>
 */
public final class ModComponents {

    private ModComponents() {}

    public static final DataComponentType<String> LEDGER_ACCOUNT =
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build();

    public static final DataComponentType<String> LEDGER_CURRENCY =
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build();

    public static final DataComponentType<Double> LEDGER_VALUE =
            DataComponentType.<Double>builder()
                    .persistent(Codec.DOUBLE)
                    .networkSynchronized(ByteBufCodecs.DOUBLE)
                    .build();
}
