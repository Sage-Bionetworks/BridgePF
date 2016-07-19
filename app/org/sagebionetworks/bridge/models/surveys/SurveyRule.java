package org.sagebionetworks.bridge.models.surveys;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class SurveyRule {

    public enum Operator {
        EQ, // equal to
        NE, // not equal to
        LT, // less than
        GT, // greater than
        LE, // less than or equal to
        GE, // greater than or equal to
        DE  // declined to answer
    }
    
    private final Operator operator;
    private final Object value;
    // Rule should either have a skipToTarget, or have endSurvey set to true, but never both.
    // This is enforced in rules validation.
    private final String skipToTarget;
    private final Boolean endSurvey;
    
    @JsonCreator
    private SurveyRule(@JsonProperty("operator") Operator operator, @JsonProperty("value") Object value,
            @JsonProperty("skipTo") String skipToTarget, @JsonProperty("endSurvey") Boolean endSurvey) {
        this.operator = operator;
        this.value = value;
        this.skipToTarget = skipToTarget;
        this.endSurvey = Boolean.TRUE.equals(endSurvey) ? Boolean.TRUE : null;
    }
    public SurveyRule(Operator operator, Object value, String skipToTarget) {
        this(operator, value, skipToTarget, null);
    }
    public SurveyRule(Operator operator, Object value) {
        this(operator, value, null, Boolean.TRUE);
    }
    public Operator getOperator() {
        return operator;
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Object getValue() {
        return value;
    }
    @JsonProperty("skipTo")
    public String getSkipToTarget() {
        return skipToTarget;
    }
    public Boolean getEndSurvey() {
        return endSurvey;
    }

    @Override
    public int hashCode() {
        return Objects.hash(operator, value, skipToTarget, endSurvey);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        SurveyRule other = (SurveyRule) obj;
        return Objects.equals(skipToTarget, other.skipToTarget) &&
               Objects.equals(operator, other.operator) &&
               Objects.equals(value, other.value) &&
               Objects.equals(endSurvey, other.endSurvey);
    }

    @Override
    public String toString() {
        return "SurveyRule [operator=" + operator + ", value=" + value + ", skipToTarget=" + skipToTarget
                + ", endSurvey=" + endSurvey + "]";
    }
    
}
