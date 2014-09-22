package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class DateConstraints extends TimeBasedConstraints {
    
    private static EnumSet<UIHint> UI_HINTS = EnumSet.of(UIHint.DATEPICKER);

    public DateConstraints() {
        setDataType(DataType.DATE);
    }
    
    @Override
    @JsonIgnore
    public EnumSet<UIHint> getSupportedHints() {
        return UI_HINTS;
    }
}
