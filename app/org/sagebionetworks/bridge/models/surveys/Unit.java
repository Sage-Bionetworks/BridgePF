package org.sagebionetworks.bridge.models.surveys;

/**
 * Units of measure for integer and decimal constraints. Time units
 * are handled separately for duration constraints.
 */
public enum Unit {
    
    INCH("in"),
    FOOT("ft"),
    YARD("yd"),
    MILE("mi"),
    
    OUNCE("oz"),
    POUND("lb"),
    
    PINT("pt"),
    QUART("qt"),
    GALLON("gal"),
    
    CENTIMETER("cm"),
    METER("m"),
    KILOMETER("km"),

    GRAM("gm"),
    KILOGRAM("kg"),
    
    MILLILITER("mL"),
    CUBIC_CENTIMETER("ccm"),
    LITER("L"),
    CUBIC_METER("cbm");
    
    private String abbreviation;
    
    private Unit(String abbreviation) {
        this.abbreviation = abbreviation;
    }
    
    public String getAbbreviation() {
        return abbreviation;
    }
}
