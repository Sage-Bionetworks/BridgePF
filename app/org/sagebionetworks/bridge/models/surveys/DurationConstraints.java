package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

import org.sagebionetworks.bridge.json.DurationUnitDeserializer;
import org.sagebionetworks.bridge.json.LowercaseEnumJsonSerializer;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * This is now a special case of a numeric survey answer that has specified units. In 
 * a refactor after this, units will be carried up to a NumericConstraints parent class 
 * for IntegerConstraints, DecimalConstraints, and DurationConstraints. The last could 
 * be removed if we can confirm it is not used in the API.
 */
public class DurationConstraints extends IntegerConstraints {

    private DurationUnit unit;
    
    public DurationConstraints() {
        setDataType(DataType.DURATION);
        // This is not a time selector, you're selecting a duration, e.g. "how many years 
        // have you had ALS", "how many minutes on average do you exercise every week", 
        // etc. Not to be confused with a period, e.g. "from what year to what year did you 
        // smoke" -- which we currently don't support
        setSupportedHints(EnumSet.of(UIHint.NUMBERFIELD, UIHint.SLIDER));
    }
    
    @JsonSerialize(using = LowercaseEnumJsonSerializer.class)
    public DurationUnit getUnit() {
        return unit;
    }

    @JsonDeserialize(using = DurationUnitDeserializer.class)
    public void setUnit(DurationUnit unit) {
        this.unit = unit;
    }

}
