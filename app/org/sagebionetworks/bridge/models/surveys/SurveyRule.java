package org.sagebionetworks.bridge.models.surveys;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder=SurveyRule.Builder.class)
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
    private final String skipToTarget;
    private final Boolean endSurvey;
    
    private SurveyRule(Operator operator, Object value, String skipToTarget, Boolean endSurvey) {
        this.operator = operator;
        this.value = value;
        this.skipToTarget = skipToTarget;
        this.endSurvey = endSurvey;
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
    
    public static class Builder {
        private Operator operator;
        private Object value;
        private String skipToTarget;
        private Boolean endSurvey;
        
        public Builder withOperator(SurveyRule.Operator operator) {
            this.operator = operator;
            return this;
        }
        public Builder withValue(Object value) {
            this.value = value;
            return this;
        }
        @JsonProperty("skipTo")
        public Builder withSkipToTarget(String skipTo) {
            this.skipToTarget = skipTo;
            return this;
        }
        public Builder withEndSurvey(Boolean endSurvey) {
            if (Boolean.TRUE.equals(endSurvey)) {
                this.endSurvey = endSurvey;    
            }
            return this;
        }
        public SurveyRule build() {
            return new SurveyRule(operator, value, skipToTarget, endSurvey);
        }
    }
    
}
