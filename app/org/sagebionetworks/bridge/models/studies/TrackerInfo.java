package org.sagebionetworks.bridge.models.studies;

public class TrackerInfo {

    private final String name;
    private final String type;
    private final String identifier;
    private final String schemaUrl;

    public TrackerInfo(Tracker tracker) {
        name = tracker.getName();
        type = tracker.getType();
        identifier = tracker.getIdentifier();
        schemaUrl = "/api/v1/trackers/schema/" + tracker.getIdentifier();
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getSchemaUrl() {
        return schemaUrl;
    }

}
