package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class DurationConstraints extends Constraints {

    private static EnumSet<UIHint> UI_HINTS = EnumSet.of(UIHint.DATEPICKER, UIHint.DATETIMEPICKER, UIHint.TIMEPICKER);

    public DurationConstraints() {
        setDataType(DataType.DURATION);
    }

    @Override
    @JsonIgnore
    public EnumSet<UIHint> getSupportedHints() {
        return UI_HINTS;
    }
    
}
