package com.rdm.android;

/** A saved RDP endpoint and its non-secret display metadata. */
public final class ConnectionProfile {
    public long id;
    public String name = "";
    public String hostname = "";
    public int port = 3389;
    public String username = "";
    public String domain = "";
    /** Plaintext is populated only for an edit/connect operation; list queries never decrypt it. */
    public String password = "";
    public boolean favorite;
    public boolean passwordSaved;
    public long lastConnectedAt;

    public String endpoint() {
        return hostname + ":" + port;
    }

    public String accountLabel() {
        if (username == null || username.trim().isEmpty()) return "Authentication on connect";
        if (domain == null || domain.trim().isEmpty()) return username;
        return domain + "\\" + username;
    }
}
