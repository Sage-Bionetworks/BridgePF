package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class DateTimeConstraints extends TimeBaseConstraints {

    private static EnumSet<UIHint> UI_HINTS = EnumSet.of(UIHint.DATETIMEPICKER);
    
    @Override
    public String getDataType() {
        return "datetime";
    }

    @Override
    @JsonIgnore
    public EnumSet<UIHint> getSuportedHints() {
        return UI_HINTS;
    }
    
}
