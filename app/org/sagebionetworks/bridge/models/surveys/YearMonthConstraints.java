package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

import org.joda.time.YearMonth;

/**
 * Allows entry of a month and a year (no date). Represented in string form as "YYYY-MM".
 * Represented in Java by the Joda library's YearMonth object.
 */
public class YearMonthConstraints extends TimeBasedConstraints {

    private YearMonth earliestValue;
    private YearMonth latestValue;
    
    public YearMonthConstraints() {
        setDataType(DataType.YEARMONTH);
        setSupportedHints(EnumSet.of(UIHint.YEARMONTH));
    }

    public YearMonth getEarliestValue() {
        return earliestValue;
    }
    public void setEarliestValue(YearMonth earliestValue) {
        this.earliestValue = earliestValue;
    }
    public YearMonth getLatestValue() {
        return latestValue;
    }
    public void setLatestValue(YearMonth latestValue) {
        this.latestValue = latestValue;
    }    
}
