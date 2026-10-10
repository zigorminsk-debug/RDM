package com.rdm.android;

import android.net.Uri;

/** Maps a profile onto the documented FreeRDP Android `freerdp://` session URI. */
final class ConnectionUriFactory {
    private ConnectionUriFactory() { }

    static Uri build(ConnectionProfile profile) {
        String host = profile.hostname == null ? "" : profile.hostname.trim();
        if (host.indexOf(':') >= 0 && !(host.startsWith("[") && host.endsWith("]"))) {
            host = "[" + host + "]";
        }

        String username = profile.username == null ? "" : profile.username.trim();
        String authority = (username.isEmpty() ? "" : Uri.encode(username) + "@")
                + host + ":" + profile.port;
        Uri.Builder builder = new Uri.Builder()
                .scheme("freerdp")
                .encodedAuthority(authority)
                .path("/connect");

        if (profile.domain != null && !profile.domain.trim().isEmpty()) {
            builder.appendQueryParameter("d", profile.domain.trim());
        }
        if (profile.password != null && !profile.password.isEmpty()) {
            builder.appendQueryParameter("p", profile.password);
        }
        return builder.build();
    }
}
