package dev.chaosledger.chaosledger.quest;

/** Thrown when a quest operation cannot proceed (unknown id, illegal state, ...). */
public class QuestException extends RuntimeException {
    public QuestException(String message) {
        super(message);
    }

    public QuestException(String message, Throwable cause) {
        super(message, cause);
    }
}
