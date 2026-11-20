package za.co.mongala.legal.service;

import za.co.mongala.legal.model.Deadline;

import java.util.List;

/**
 * Google Calendar sync contract (FR4 / US5).
 * DEMO mode simulates the sync; CONNECTED mode calls the backend which uses
 * the real Google Calendar API (or the local .ics gateway).
 */
public interface ICalendarSyncService {

    String sync(Deadline d);

    String syncAll(List<Deadline> deadlines);
}
