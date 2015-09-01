package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

public class TimeConstraints extends Constraints {

    public TimeConstraints() {
        setDataType(DataType.TIME);
        setSupportedHints(EnumSet.of(UIHint.TIMEPICKER));
    }

}
