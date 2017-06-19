package org.sagebionetworks.bridge.models.surveys;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * <p>
 * Survey rules allow some logic to be embedded in a survey. The value of a rule is compared (in the manner indicated by
 * the operator) to the user's answer to a question after the user has given an answer, and an action is taken. This can
 * include declining to answer the question ("de" operator) or always doing the given action ("always operator).
 * </p>
 * 
 * <p>
 * The rules in a survey come in two categories: <em>navigation rules</em> (skipTo, endSurvey) immediately end the
 * evaluation of rules in favor of navigating to a new context; while <em>state rules</em> (assignDataGroup) change the
 * state of the participant, but rules should continue to be evaluated.
 * </p>
 * 
 */
@JsonDeserialize(builder=SurveyRule.Builder.class)
public final class SurveyRule {

    public enum Operator {
        EQ,    // equal to
        NE,    // not equal to
        LT,    // less than
        GT,    // greater than
        LE,    // less than or equal to
        GE,    // greater than or equal to
        DE,    // declined to answer
        ALWAYS // always apply this rule
    }
    
    private final Operator operator;
    private final Object value;
    private final String skipToTarget;
    private final Boolean endSurvey;
    private final String assignDataGroup;
    
    private SurveyRule(Operator operator, Object value, String skipToTarget, Boolean endSurvey,
            String assignDataGroup) {
        this.operator = operator;
        this.value = value;
        this.skipToTarget = skipToTarget;
        this.endSurvey = endSurvey;
        this.assignDataGroup = assignDataGroup;
    }
    public Operator getOperator() {
        return operator;
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Object getValue() {
        return value;
    }
    /**
     * If the rule condition is true, advanced to the question or information screen indicated by the 
     * identifier. Do not evaluate further rules in the list of rules. The identifier must be later 
     * in the list of survey questions (you cannot backtrack). If the rule is false, continue 
     * evaluating rules or continue with the next question it the study.
     */
    @JsonProperty("skipTo")
    public String getSkipToTarget() {
        return skipToTarget;
    }
    /**
     * If the rule condition is true, stop evaluating rules and end the survey immediately. Existing 
     * survey answers should be sent to the survey. If the rule is false, continue evaluating rules or 
     * continue with the next question it the study.
     */
    public Boolean getEndSurvey() {
        return endSurvey;
    }
    /**
     * If the rule condition is true, add this data group to the participant's data groups. Otherwise, 
     * remove this data group from the participant's data groups. Continue evaluating the rules after 
     * this rule (do not end evaluation, this is not a navigation rule).
     */
    public String getAssignDataGroup() {
        return assignDataGroup;
    }

    @Override
    public int hashCode() {
        return Objects.hash(operator, value, skipToTarget, endSurvey, assignDataGroup);
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
               Objects.equals(endSurvey, other.endSurvey) &&
               Objects.equals(assignDataGroup, other.assignDataGroup);
    }

    @Override
    public String toString() {
        return "SurveyRule [operator=" + operator + ", value=" + value + ", skipToTarget=" + skipToTarget
                + ", endSurvey=" + endSurvey + ", assignDataGroup=" + assignDataGroup + "]";
    }

    public static class Builder {
        private Operator operator;
        private Object value;
        private String skipToTarget;
        private Boolean endSurvey;
        private String assignDataGroup;

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
        public Builder withAssignDataGroup(String dataGroup) {
            this.assignDataGroup = dataGroup;
            return this;
        }
        public SurveyRule build() {
            return new SurveyRule(operator, value, skipToTarget, endSurvey, assignDataGroup);
        }
    }
    
}
