package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class TimeConstraints extends Constraints {
    
    private static EnumSet<UIHint> UI_HINTS = EnumSet.of(UIHint.TIMEPICKER);
    
    @Override
    public String getDataType() {
        return "time";
    }

    @Override
    @JsonIgnore
    public EnumSet<UIHint> getSuportedHints() {
        return UI_HINTS;
    }
}
