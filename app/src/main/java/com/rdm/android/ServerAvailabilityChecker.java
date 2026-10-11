package com.rdm.android;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tests whether an RDP endpoint's TCP port accepts a connection without blocking the UI. */
final class ServerAvailabilityChecker {
    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "RDM-server-check");
        thread.setDaemon(true);
        return thread;
    });

    interface Callback {
        void onResult(boolean available);
    }

    private ServerAvailabilityChecker() { }

    static void check(String hostname, int port, Callback callback) {
        EXECUTOR.execute(() -> {
            boolean available = false;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(hostname, port), CONNECT_TIMEOUT_MS);
                available = true;
            } catch (IOException | SecurityException ignored) {
                // A timeout, refused connection, DNS failure, or denied network access is unavailable.
            }
            callback.onResult(available);
        });
    }
}
