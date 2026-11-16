package dev.chaosledger.chaosledger.economy;

import java.util.Objects;

/**
 * Definition of a currency ChaosLedger knows about.
 *
 * <p>A currency has a unique code ({@code CC}, {@code GOLD}, ...), a display name
 * ({@code "Copper coins"}), a short symbol used in chat and the web panel
 * ({@code "CC"}), and a default amount handed out to newly registered players.</p>
 */
public record Currency(String code, String displayName, String symbol, double defaultStarting) {

    public Currency {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(symbol, "symbol");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (Double.isNaN(defaultStarting) || Double.isInfinite(defaultStarting)) {
            throw new IllegalArgumentException("defaultStarting must be finite");
        }
    }
}
