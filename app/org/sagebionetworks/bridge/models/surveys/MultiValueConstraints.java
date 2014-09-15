package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * The only way to constrain a multiple value answer is through an enumeration of the 
 * values that are allowed. However, an enumeration is not required. 
 * 
 * Note that if a user can enter an "other" value, even if there is an enumeration of 
 * the allowable values, then there will be no validation on the submitted answer, 
 * except to verify that it is the right data type.
 */
public class MultiValueConstraints extends Constraints {

    private static EnumSet<UIHint> UI_HINTS = EnumSet.of(UIHint.CHECKBOX, UIHint.COMBOBOX, UIHint.LIST,
            UIHint.RADIOBUTTON, UIHint.SELECT, UIHint.SLIDER);
    
    private List<SurveyQuestionOption> enumeration;
    private boolean allowOther = false;
    
    public MultiValueConstraints() {
        setDataType(DataType.STRING);
    }
    
    public MultiValueConstraints(DataType dataType) {
        setDataType(dataType);
    }
    
    @Override
    @JsonIgnore
    public EnumSet<UIHint> getSuportedHints() {
        return UI_HINTS;
    }
    public List<SurveyQuestionOption> getEnumeration() {
        return enumeration;
    }
    public void setEnumeration(List<SurveyQuestionOption> enumeration) {
        this.enumeration = enumeration;
    }
    public boolean getAllowOther() {
        return allowOther;
    }
    public void setAllowOther(boolean allowOther) {
        this.allowOther = allowOther;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (allowOther ? 1231 : 1237);
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
        MultiValueConstraints other = (MultiValueConstraints) obj;
        if (allowOther != other.allowOther)
            return false;
        if (enumeration == null) {
            if (other.enumeration != null)
                return false;
        } else if (!enumeration.equals(other.enumeration))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "MultiValueConstraints [enumeration=" + enumeration + ", allowOther=" + allowOther + "]";
    }
    
}
