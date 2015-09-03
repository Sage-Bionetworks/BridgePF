package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;
import java.util.Objects;

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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hash(earliestValue);
        result = prime * result + Objects.hash(latestValue);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj) || getClass() != obj.getClass())
            return false;
        DateConstraints other = (DateConstraints) obj;
        return (Objects.equals(earliestValue, other.earliestValue) &&
                Objects.equals(latestValue, other.latestValue));            
    }
    
}
