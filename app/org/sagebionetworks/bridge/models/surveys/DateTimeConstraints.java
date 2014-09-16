package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class DateTimeConstraints extends TimeBasedConstraints {

    private static EnumSet<UIHint> UI_HINTS = EnumSet.of(UIHint.DATETIMEPICKER);
    
    public DateTimeConstraints() {
        setDataType(DataType.DATETIME);
    }
    
    @Override
    @JsonIgnore
    public EnumSet<UIHint> getSupportedHints() {
        return UI_HINTS;
    }
    
}
