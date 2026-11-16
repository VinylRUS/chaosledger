package dev.chaosledger.chaosledger.reward;

/** Unrecoverable error in a reward operation. */
public class RewardException extends RuntimeException {
    public RewardException(String message) {
        super(message);
    }

    public RewardException(String message, Throwable cause) {
        super(message, cause);
    }
}
