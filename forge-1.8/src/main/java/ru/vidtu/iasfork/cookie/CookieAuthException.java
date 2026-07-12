package ru.vidtu.iasfork.cookie;

/**
 * Cookie import failure with optional lang key for UI.
 */
public class CookieAuthException extends Exception {
    private static final long serialVersionUID = 1L;
    private final String langKey;

    public CookieAuthException(String message) {
        this(message, null);
    }

    public CookieAuthException(String message, String langKey) {
        super(message);
        this.langKey = langKey;
    }

    public String langKey() {
        return langKey;
    }
}
