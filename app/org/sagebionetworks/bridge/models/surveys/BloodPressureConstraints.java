package org.sagebionetworks.bridge.models.surveys;

import static org.sagebionetworks.bridge.models.surveys.UIHint.BLOODPRESSURE;

import java.util.EnumSet;

public class BloodPressureConstraints extends Constraints {
    public BloodPressureConstraints() {
        setDataType(DataType.STRING);
        setSupportedHints(EnumSet.of(BLOODPRESSURE));
    }
}
