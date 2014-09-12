package org.sagebionetworks.bridge.models.surveys;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * These are constraints that can have all their values enumerated. 
 * Certain UI controls, such as pickers or select controls, require 
 * this. It is usually easier for users if all the values can be 
 * specified.
 * 
 * When values are enumerated, than the min/max value or min/max length
 * properties are of no use, ditto for scale and for precision. So I 
 * don't think the class hierarchy is actually correct here.
 */
public abstract class EnumerableConstraints extends Constraints {

    protected List<SurveyQuestionOption> enumeration;
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public List<SurveyQuestionOption> getEnumeration() {
        return enumeration;
    }
    public void setEnumeration(List<SurveyQuestionOption> enumeration) {
        this.enumeration = enumeration;
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((enumeration == null) ? 0 : enumeration.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        EnumerableConstraints other = (EnumerableConstraints) obj;
        if (enumeration == null) {
            if (other.enumeration != null)
                return false;
        } else if (!enumeration.equals(other.enumeration))
            return false;
        return true;
    }
}
