package za.co.mongala.legal.backend.service;

import io.javalin.http.sse.SseClient;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal in-process event bus that pushes "new-message" notifications to every
 * connected browser client (SSE) the moment a message is saved, so the unread
 * alert bell updates instantly instead of waiting for a poll interval.
 */
public final class EventBus {

    private static final List<SseClient> CLIENTS = new CopyOnWriteArrayList<>();

    private EventBus() {}

    /** Registers a client and automatically removes it when it disconnects. */
    public static void subscribe(SseClient client) {
        CLIENTS.add(client);
        client.onClose(() -> CLIENTS.remove(client));
    }

    /** Broadcasts an event to all connected clients. */
    public static void publish(String event, String data) {
        for (SseClient c : CLIENTS) {
            try {
                c.sendEvent(event, data);
            } catch (Exception e) {
                CLIENTS.remove(c); // dead connection
            }
        }
    }

    public static int subscriberCount() {
        return CLIENTS.size();
    }
}
