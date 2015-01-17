package org.sagebionetworks.bridge.models.surveys;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.joda.time.Period;

/**
 * Duration is going to become another name for the integer data type, 
 * with a time unit. However the specs say you can submit an ISO 8601 
 * duration string for this value, so this converter will convert that 
 * to an integer in the given units, if this older format is being used. 
 * If/when we can confirm no application uses this older format, this 
 * can be removed.
 * 
 * This class does require a time unit, which is new, if this doesn't 
 * cause anyone problems, then this type isn't in use and we can remove
 * this class.
 */
public class DurationToIntegerConverter {

    public String convert(String value, Unit unit) {
        // value is not a ISO 8601 Duration string
        if (StringUtils.isEmpty(value) || NumberUtils.isNumber(value)) {
            return value;
        }

        // Duration string must have the units we are looking for, it cannot measure the duration 
        // in other units because some conversions, particularly to months, are ambiguous
        Period period = Period.parse(value);
        int periodUnits = periodInUnits(period, unit);
        if (periodUnits > 0) {
            return Integer.toString(periodUnits);
        }

        // This is the worst case: a duration string has been submitted with units that are different
        // than the required units of the question. Throw an exception
        throw new IllegalArgumentException("ISO 8601 duration does not specify a duration in the units of the question (e.g. PT60M for a duration measured in hours; use PT1H instead)");
    }
    
    private int periodInUnits(Period period, Unit units) {
        switch(units) {
        case SECONDS:
            return period.getSeconds();
        case MINUTES:
            return period.getMinutes();
        case HOURS:
            return period.getHours();
        case DAYS:
            return period.getDays();
        case WEEKS:
            return period.getWeeks();
        case MONTHS:
            return period.getMonths();
        case YEARS:
            return period.getYears();
        default:
            return 0;
        }
    }
    
}
