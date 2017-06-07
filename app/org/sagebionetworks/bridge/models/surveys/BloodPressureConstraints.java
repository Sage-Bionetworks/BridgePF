package org.sagebionetworks.bridge.models.surveys;

import static org.sagebionetworks.bridge.models.surveys.UIHint.BLOODPRESSURE;

import java.util.EnumSet;

public class BloodPressureConstraints extends Constraints {
    public static final EnumSet<Unit> UNITS = EnumSet.of(Unit.MILLIMETERS_MERCURY);

    private Unit unit;

    public BloodPressureConstraints() {
        setDataType(DataType.BLOODPRESSURE);
        setSupportedHints(EnumSet.of(BLOODPRESSURE));
    }

    public Unit getUnit() {
        return unit;
    }

    public void setUnit(Unit unit) {
        this.unit = unit;
    }
}
