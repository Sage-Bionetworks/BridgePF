package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class DateConstraints extends TimeBaseConstraints {
    
    private static EnumSet<UIHint> UI_HINTS = EnumSet.of(UIHint.DATEPICKER);

    @Override
    public String getDataType() {
        return "date";
    }

    @Override
    @JsonIgnore
    public EnumSet<UIHint> getSuportedHints() {
        return UI_HINTS;
    }
    
}
