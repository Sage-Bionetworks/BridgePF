package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;
import java.util.List;

import org.sagebionetworks.bridge.validators.Messages;

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
    private boolean allowMultiple = false;
    
    public MultiValueConstraints() {
        setDataType(DataType.STRING);
    }
    
    public MultiValueConstraints(DataType dataType) {
        setDataType(dataType);
    }
    
    @Override
    @JsonIgnore
    public EnumSet<UIHint> getSupportedHints() {
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
    public boolean getAllowMultiple() {
        return allowMultiple;
    }
    public void setAllowMultiple(boolean allowMultiple) {
        this.allowMultiple = allowMultiple;
    }
    public void validate(Messages messages, SurveyAnswer answer) {
        Object value = answer.getAnswer();
        if (allowMultiple) {
            // The only acceptable type here is an array, then validate all members of the array.
            if (!(value instanceof Object[])) {
                messages.add("Answer should be an array of values");
                return;
            }
            Object[] array = (Object[])value;
            for (int i=0; i < array.length; i++) {
                validateType(messages, array[i], "Array value #"+i);
            }
            if (messages.isEmpty() && !allowOther) {
                for (int i=0; i < array.length; i++) {
                    if (!isEnumeratedValue(array[i])) {
                        messages.add("Answer #%s is not one of the enumerated values for this question", i);
                    }
                }
            }
        } else {
            validateType(messages, value, "Answer");
            if (messages.isEmpty() && !allowOther && !isEnumeratedValue(value)) {
                messages.add("Answer is not one of the enumerated values for this question");
            }
        }
    }
    private void validateType(Messages messages, Object value, String name) {
        if (getDataType().getCastClass() != value.getClass()) {
            messages.add("%s is the wrong type (it's %s but it should be %s", name, value.getClass().getSimpleName(),
                    getDataType().getCastClass().getSimpleName());
        }
    }
    private boolean isEnumeratedValue(Object value) {
        for (SurveyQuestionOption option : enumeration) {
            if (option.getValue() instanceof Number && value instanceof Number) {
                double d1 = ((Number)option.getValue()).doubleValue();
                double d2 = ((Number)value).doubleValue();
                if (d1 == d2) {
                    return true;
                }
            } else {
                if (option.getValue().equals(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (allowMultiple ? 1231 : 1237);
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
        if (allowMultiple != other.allowMultiple)
            return false;
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
        return "MultiValueConstraints [enumeration=" + enumeration + ", allowOther=" + allowOther + ", allowMultiple="
                + allowMultiple + "]";
    }
}
