package ru.vidtu.iasfork.cookie;

/**
 * A single Netscape-format HTTP cookie entry.
 */
public final class CookieEntry {
    private final String domain;
    private final String path;
    private final boolean secure;
    private final String name;
    private final String value;

    public CookieEntry(String domain, String path, boolean secure, String name, String value) {
        this.domain = domain;
        this.path = path;
        this.secure = secure;
        this.name = name;
        this.value = value;
    }

    public String domain() {
        return domain;
    }

    public String path() {
        return path;
    }

    public boolean secure() {
        return secure;
    }

    public String name() {
        return name;
    }

    public String value() {
        return value;
    }

    public String pair() {
        return name + '=' + value;
    }
}
