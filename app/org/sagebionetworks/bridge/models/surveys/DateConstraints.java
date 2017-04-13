package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

import org.joda.time.LocalDate;

public class DateConstraints extends TimeBasedConstraints {

    private LocalDate earliestValue;
    private LocalDate latestValue;
    
    public DateConstraints() {
        setDataType(DataType.DATE);
        setSupportedHints(EnumSet.of(UIHint.DATEPICKER));
    }
    
    public LocalDate getEarliestValue() {
        return earliestValue;
    }
    public void setEarliestValue(LocalDate earliestValue) {
        this.earliestValue = earliestValue;
    }
    public LocalDate getLatestValue() {
        return latestValue;
    }
    public void setLatestValue(LocalDate latestValue) {
        this.latestValue = latestValue;
    }
}
