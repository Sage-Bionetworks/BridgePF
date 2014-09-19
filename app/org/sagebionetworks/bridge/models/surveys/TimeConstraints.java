package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class TimeConstraints extends Constraints {
    
    private static EnumSet<UIHint> UI_HINTS = EnumSet.of(UIHint.TIMEPICKER);
    
    public TimeConstraints() {
        setDataType(DataType.TIME);
    }
    
    @Override
    @JsonIgnore
    public EnumSet<UIHint> getSupportedHints() {
        return UI_HINTS;
    }
}
