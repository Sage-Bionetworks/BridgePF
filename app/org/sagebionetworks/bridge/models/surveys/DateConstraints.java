package org.sagebionetworks.bridge.models.surveys;

public class DateConstraints extends TimeBaseConstraints {

    @Override
    public String getDataType() {
        return "date";
    }
    
}
