package org.sagebionetworks.bridge.models.surveys;

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
    CUBIC_METERS,

    MILLIMETERS_MERCURY;
}
