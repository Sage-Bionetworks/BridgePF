package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

public class DateTimeConstraints extends TimeBasedConstraints {

    public DateTimeConstraints() {
        setDataType(DataType.DATETIME);
        setSupportedHints(EnumSet.of(UIHint.DATETIMEPICKER));
    }
    
}
