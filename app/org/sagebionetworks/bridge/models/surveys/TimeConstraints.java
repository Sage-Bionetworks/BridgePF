package org.sagebionetworks.bridge.models.surveys;

public class TimeConstraints extends TimeBaseConstraints {
    @Override
    public String getDataType() {
        return "date";
    }
}
