package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

public class DateConstraints extends TimeBasedConstraints {
    
    public DateConstraints() {
        setDataType(DataType.DATE);
        setSupportedHints(EnumSet.of(UIHint.DATEPICKER));
    }

}
