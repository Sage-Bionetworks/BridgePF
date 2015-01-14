package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

import org.sagebionetworks.bridge.json.DurationUnitDeserializer;
import org.sagebionetworks.bridge.json.LowercaseEnumJsonSerializer;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public class DurationConstraints extends Constraints {

    private DurationUnit unit;
    private Long minValue;
    private Long maxValue;
    
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

    public Long getMinValue() {
        return minValue;
    }

    public void setMinValue(Long minValue) {
        this.minValue = minValue;
    }

    public Long getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(Long maxValue) {
        this.maxValue = maxValue;
    }
}
