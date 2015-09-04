package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;
import java.util.Objects;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.DateTimeDeserializer;
import org.sagebionetworks.bridge.json.DateTimeSerializer;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public class DateTimeConstraints extends TimeBasedConstraints {
    
    private DateTime earliestValue;
    private DateTime latestValue;

    public DateTimeConstraints() {
        setDataType(DataType.DATETIME);
        setSupportedHints(EnumSet.of(UIHint.DATETIMEPICKER));
    }
    @JsonDeserialize(using = DateTimeDeserializer.class)
    public DateTime getEarliestValue() {
        return earliestValue;
    }
    @JsonSerialize(using = DateTimeSerializer.class)
    public void setEarliestValue(DateTime earliestValue) {
        this.earliestValue = earliestValue;
    }
    @JsonDeserialize(using = DateTimeDeserializer.class)
    public DateTime getLatestValue() {
        return latestValue;
    }
    @JsonSerialize(using = DateTimeSerializer.class)
    public void setLatestValue(DateTime latestValue) {
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
        DateTimeConstraints other = (DateTimeConstraints) obj;
        return (Objects.equals(earliestValue, other.earliestValue) &&
                Objects.equals(latestValue, other.latestValue));            
    }
    
}
