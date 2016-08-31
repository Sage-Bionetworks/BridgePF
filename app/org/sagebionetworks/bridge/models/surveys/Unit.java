package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

/**
 * Units of measure for integer and decimal constraints. Time units
 * are grouped in DURATION_UNITS for the duration question type (really 
 * just a special case of an integer question with a unit). 
 */
public enum Unit {
    
    SECONDS,
    MINUTES,
    HOURS,
    DAYS,
    WEEKS,
    MONTHS,
    YEARS,
    
    INCHES,
    FEET,
    YARDS,
    MILES,
    
    OUNCES,
    POUNDS,
    
    PINTS,
    QUARTS,
    GALLONS,
    
    CENTIMETERS,
    METERS,
    KILOMETERS,

    GRAMS,
    KILOGRAMS,
    
    MILLILITERS,
    CUBIC_CENTIMETERS,
    LITERS,
    CUBIC_METERS;
    
    public static final EnumSet<Unit> DURATION_UNITS = EnumSet.of(SECONDS, MINUTES, HOURS, DAYS, WEEKS, MONTHS, YEARS);

    /*
     * We need to know what the longest unit is, so we can reserve the proper sized column in our schemas. Currently,
     * this is cubic_centimeters. If this ever changes, we need to update this.
     */
    public static final int MAX_STRING_LENGTH = CUBIC_CENTIMETERS.name().length();
}
