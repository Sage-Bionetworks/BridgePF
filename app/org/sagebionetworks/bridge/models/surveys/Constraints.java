package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;
import java.util.List;

import org.sagebionetworks.bridge.json.DataTypeJsonDeserializer;
import org.sagebionetworks.bridge.json.LowercaseEnumJsonSerializer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Lists;

@JsonTypeInfo( use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "dataType")
@JsonSubTypes({
    @Type(name="multivalue", value=MultiValueConstraints.class),
    @Type(name="boolean", value=BooleanConstraints.class),
    @Type(name="integer", value=IntegerConstraints.class),
    @Type(name="decimal", value=DecimalConstraints.class),
    @Type(name="string", value=StringConstraints.class),
    @Type(name="datetime", value=DateTimeConstraints.class),
    @Type(name="date", value=DateConstraints.class),
    @Type(name="time", value=TimeConstraints.class),
    @Type(name="duration", value=DurationConstraints.class)
})
public abstract class Constraints {

    private EnumSet<UIHint> hints;
    private List<SurveyRule> rules = Lists.newArrayList();
    private DataType dataType;
    
    @JsonIgnore
    public EnumSet<UIHint> getSupportedHints() {
        return hints;
    }
    public void setSupportedHints(EnumSet<UIHint> hints) {
        this.hints = hints;
    }
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
    // NOTE: This shouldn't be necessary as I understand it. When we serialize this, 
    // we use the BridgeObjectMapper which adds the "type" property to all objects, 
    // or it's supposed to. But SurveyControllerTest says otherwise.
    public String getType() {
        return this.getClass().getSimpleName();
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((dataType == null) ? 0 : dataType.hashCode());
        result = prime * result + ((hints == null) ? 0 : hints.hashCode());
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
        if (hints == null) {
            if (other.hints != null)
                return false;
        } else if (!hints.equals(other.hints))
            return false;
        if (rules == null) {
            if (other.rules != null)
                return false;
        } else if (!rules.equals(other.rules))
            return false;
        return true;
    }
}
