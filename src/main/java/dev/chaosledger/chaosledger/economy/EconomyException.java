package dev.chaosledger.chaosledger.economy;

/** Unrecoverable error in an economy operation (insufficient funds, bad currency, ...). */
public class EconomyException extends RuntimeException {
    public EconomyException(String message) {
        super(message);
    }

    public EconomyException(String message, Throwable cause) {
        super(message, cause);
    }
}
