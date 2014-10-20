package org.sagebionetworks.bridge.models.surveys;

import org.sagebionetworks.bridge.json.LowercaseEnumJsonSerializer;
import org.sagebionetworks.bridge.json.OperatorJsonDeserializer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public class SurveyRule {

    public enum Operator {
        EQ, // equal to
        NE, // not equal to
        LT, // less than
        GT, // greater than
        LE, // less than or equal to
        GE, // greater than or equal to
        DE  // declined to answer
    }
    
    private Operator operator;
    private Object value;
    private String skipToTarget;
    
    public SurveyRule() {
    }
    public SurveyRule(Operator operator, Object value, String skipToTarget) {
        this.operator = operator;
        this.value = value;
        this.skipToTarget = skipToTarget;
    }
    @JsonSerialize(using = LowercaseEnumJsonSerializer.class)
    public Operator getOperator() {
        return operator;
    }
    @JsonDeserialize(using = OperatorJsonDeserializer.class)
    public void setOperator(Operator operator) {
        this.operator = operator;
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Object getValue() {
        return value;
    }
    public void setValue(Object value) {
        this.value = value;
    }
    @JsonProperty("skipTo")
    public String getSkipToTarget() {
        return skipToTarget;
    }
    public void setSkipToTarget(String skipToTarget) {
        this.skipToTarget = skipToTarget;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((skipToTarget == null) ? 0 : skipToTarget.hashCode());
        result = prime * result + ((operator == null) ? 0 : operator.hashCode());
        result = prime * result + ((value == null) ? 0 : value.hashCode());
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
        SurveyRule other = (SurveyRule) obj;
        if (skipToTarget == null) {
            if (other.skipToTarget != null)
                return false;
        } else if (!skipToTarget.equals(other.skipToTarget))
            return false;
        if (operator != other.operator)
            return false;
        if (value == null) {
            if (other.value != null)
                return false;
        } else if (!value.equals(other.value))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "SurveyRule [operator=" + operator + ", value=" + value + ", skipToTarget=" + skipToTarget + "]";
    }
    
}
