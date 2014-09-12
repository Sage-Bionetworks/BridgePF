package org.sagebionetworks.bridge.models.surveys;

public class DateTimeConstraints extends TimeBaseConstraints {

    @Override
    public String getDataType() {
        return "datetime";
    }
    
}
