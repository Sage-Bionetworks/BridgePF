package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

public class DurationConstraints extends NumericalConstraints {

    public static final EnumSet<Unit> DURATION_UNITS = EnumSet.of(Unit.SECONDS, Unit.MINUTES, Unit.HOURS, Unit.DAYS, Unit.WEEKS, Unit.MONTHS, Unit.YEARS);

    public DurationConstraints() {
        super();
        setDataType(DataType.DURATION);
    }
}
