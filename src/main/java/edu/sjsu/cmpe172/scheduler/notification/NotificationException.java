package edu.sjsu.cmpe172.scheduler.notification;

/**
 * A remote notification call failed.
 *
 * @see #retryable() true for timeouts, refused connections and 5xx answers (trying again may
 *      work); false for 4xx answers (the request itself is wrong, so resending cannot help)
 */
public class NotificationException extends RuntimeException {

    private final boolean retryable;

    public NotificationException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
