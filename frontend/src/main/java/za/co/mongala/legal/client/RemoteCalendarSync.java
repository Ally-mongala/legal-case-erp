package za.co.mongala.legal.client;

import za.co.mongala.legal.model.Deadline;
import za.co.mongala.legal.service.ICalendarSyncService;

import java.util.List;
import java.util.Map;

/**
 * CONNECTED-mode calendar sync: delegates to the backend, which performs the
 * real sync through the configured CalendarGateway (Google Calendar API or the
 * local .ics exporter).
 */
public class RemoteCalendarSync implements ICalendarSyncService {

    private final ApiClient api;

    public RemoteCalendarSync(ApiClient api) {
        this.api = api;
    }

    @Override
    public String sync(Deadline d) {
        Map<String, Object> resp = api.post("/api/calendar/sync",
                Map.of("deadlineIds", List.of(d.getId())), Map.class);
        d.setSynced(true);
        return String.valueOf(resp.getOrDefault("message", "Synced"));
    }

    @Override
    public String syncAll(List<Deadline> deadlines) {
        List<String> ids = deadlines.stream().map(Deadline::getId).toList();
        Map<String, Object> resp = api.post("/api/calendar/sync", Map.of("deadlineIds", ids), Map.class);
        deadlines.forEach(d -> d.setSynced(true));
        return String.valueOf(resp.getOrDefault("message", "Synced"));
    }
}
