package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.json.DataTypeJsonDeserializer;
import org.sagebionetworks.bridge.json.LowercaseEnumJsonSerializer;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Lists;
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
    
    private List<SurveyRule> rules = Lists.newArrayList();
    private DataType dataType;
    
    public abstract EnumSet<UIHint> getSuportedHints();
    
    @JsonSerialize(using = LowercaseEnumJsonSerializer.class)
    public DataType getDataType() {
        return dataType;
    };
    @JsonDeserialize(using = DataTypeJsonDeserializer.class)
    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }
    public List<SurveyRule> getRules() {
        return rules;
    }
    public void setRules(List<SurveyRule> rules) {
        this.rules = rules;
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((dataType == null) ? 0 : dataType.hashCode());
        result = prime * result + ((rules == null) ? 0 : rules.hashCode());
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
        if (dataType != other.dataType)
            return false;
        if (rules == null) {
            if (other.rules != null)
                return false;
        } else if (!rules.equals(other.rules))
            return false;
        return true;
    }
}
