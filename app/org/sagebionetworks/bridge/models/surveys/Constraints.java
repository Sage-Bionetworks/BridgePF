package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;
import java.util.Map;

import org.sagebionetworks.bridge.json.DataTypeJsonDeserializer;
import org.sagebionetworks.bridge.json.LowercaseEnumJsonSerializer;
import org.sagebionetworks.bridge.validators.Messages;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Maps;

public abstract class Constraints {

    public static Map<String,Class<? extends Constraints>> CLASSES = Maps.newHashMap();
    static {
        CLASSES.put("boolean", BooleanConstraints.class);
        CLASSES.put("integer", IntegerConstraints.class);
        CLASSES.put("decimal", DecimalConstraints.class);
        CLASSES.put("string", StringConstraints.class);
        CLASSES.put("datetime", DateTimeConstraints.class);
        CLASSES.put("date", DateConstraints.class);
        CLASSES.put("time", TimeConstraints.class);
        CLASSES.put("duration", DurationConstraints.class);
    }
    
    private DataType dataType;
    
    public abstract EnumSet<UIHint> getSupportedHints();
    
    public void validate(Messages messages, SurveyAnswer answer) {
        // noop
    }
    
    @JsonSerialize(using = LowercaseEnumJsonSerializer.class)
    public DataType getDataType() {
        return dataType;
    };
    @JsonDeserialize(using = DataTypeJsonDeserializer.class)
    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + getDataType().hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Constraints other = (Constraints) obj;
        if (!getDataType().equals(other.getDataType()))
            return false;
        return true;
    }
}
