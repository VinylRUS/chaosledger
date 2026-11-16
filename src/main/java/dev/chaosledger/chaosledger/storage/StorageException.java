package dev.chaosledger.chaosledger.storage;

/**
 * Thrown when a storage operation fails. Storage failures are unrecoverable
 * for a single request but must not crash the server thread; callers wrap them
 * into user-facing error messages.
 */
public class StorageException extends RuntimeException {
    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
