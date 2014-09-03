package org.sagebionetworks.bridge.models;

public class TrackerInfo {

    private final String name;
    private final String type;
    private final Long id;
    private final String schemaUrl;

    public TrackerInfo(Tracker tracker) {
        name = tracker.getName();
        type = tracker.getType();
        id = tracker.getId();
        schemaUrl = "/api/v1/trackers/schema/" + tracker.getId().toString();
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public Long getId() {
        return id;
    }

    public String getSchemaUrl() {
        return schemaUrl;
    }

}
