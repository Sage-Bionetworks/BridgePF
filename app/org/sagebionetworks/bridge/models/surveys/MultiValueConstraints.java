package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class MultiValueConstraints extends Constraints {

    private static EnumSet<UIHint> UI_HINTS = EnumSet.of(UIHint.CHECKBOX, UIHint.COMBOBOX, UIHint.LIST,
            UIHint.RADIOBUTTON, UIHint.SELECT, UIHint.SLIDER);
    
    private List<SurveyQuestionOption> enumeration;
    
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
        MultiValueConstraints other = (MultiValueConstraints) obj;
        if (enumeration == null) {
            if (other.enumeration != null)
                return false;
        } else if (!enumeration.equals(other.enumeration))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "MultiValueConstraints [dataType=" + getDataType().name() + ",  enumeration=" + enumeration + "]";
    }
    
}
