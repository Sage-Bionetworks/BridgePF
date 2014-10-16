package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

public class DurationConstraints extends Constraints {

    public DurationConstraints() {
        setDataType(DataType.DURATION);
        setSupportedHints(EnumSet.of(UIHint.DATEPICKER, UIHint.DATETIMEPICKER, UIHint.TIMEPICKER));
    }
    
}
