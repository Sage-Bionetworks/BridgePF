package org.sagebionetworks.bridge.models;

public class TrackerInfo {

    private String name;
    private String type;
    private Long id;
    private String schemaUrl;

    public TrackerInfo(Tracker tracker) {
        name = tracker.getName();
        type = tracker.getType();
        id = tracker.getId();
        schemaUrl = "/api/trackers/schema/" + tracker.getId().toString();
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
