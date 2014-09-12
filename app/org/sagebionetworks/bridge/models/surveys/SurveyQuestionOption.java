package org.sagebionetworks.bridge.models.surveys;

import com.fasterxml.jackson.annotation.JsonInclude;

public class SurveyQuestionOption {

    private String label;
    private Object value; // what type can this be? It has to be similar to the type of the encompassing constraint. Grrr.
    private String skipToQuestionIdentifier;
    
    public SurveyQuestionOption() {
    }
    
    public SurveyQuestionOption(String label, Object value, String skipToIdentifierQuestion) {
        this.label = label;
        this.value = value;
        this.skipToQuestionIdentifier = skipToIdentifierQuestion;
    }
    
    public String getLabel() {
        return label;
    }
    public void setLabel(String label) {
        this.label = label;
    }
    public Object getValue() {
        return value;
    }
    public void setValue(Object value) {
        this.value = value;
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getSkipToQuestionIdentifier() {
        return skipToQuestionIdentifier;
    }
    public void setSkipToQuestionIdentifier(String skipToQuestionIdentifier) {
        this.skipToQuestionIdentifier = skipToQuestionIdentifier;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((label == null) ? 0 : label.hashCode());
        result = prime * result + ((skipToQuestionIdentifier == null) ? 0 : skipToQuestionIdentifier.hashCode());
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
        SurveyQuestionOption other = (SurveyQuestionOption) obj;
        if (label == null) {
            if (other.label != null)
                return false;
        } else if (!label.equals(other.label))
            return false;
        if (skipToQuestionIdentifier == null) {
            if (other.skipToQuestionIdentifier != null)
                return false;
        } else if (!skipToQuestionIdentifier.equals(other.skipToQuestionIdentifier))
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
        return "SurveyQuestionOption [label=" + label + ", value=" + value + ", skipToQuestionIdentifier="
                + skipToQuestionIdentifier + "]";
    }
    
}
