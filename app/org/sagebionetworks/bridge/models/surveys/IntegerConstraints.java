package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class IntegerConstraints extends EnumerableConstraints {

    private static EnumSet<UIHint> UI_HINTS = EnumSet.of(UIHint.CHECKBOX, UIHint.COMBOBOX, UIHint.LIST,
            UIHint.NUMBERFIELD, UIHint.RADIOBUTTON, UIHint.SELECT, UIHint.SLIDER);
    
    private int minValue = 0;
    private int maxValue = 100;
    private int step = 1;
    
    @Override
    public String getDataType() {
        return "integer";
    }
    @Override
    @JsonIgnore
    public EnumSet<UIHint> getSuportedHints() {
        return UI_HINTS;
    }
    public int getMinValue() {
        return minValue;
    }
    public void setMinValue(int minValue) {
        this.minValue = minValue;
    }
    public int getMaxValue() {
        return maxValue;
    }
    public void setMaxValue(int maxValue) {
        this.maxValue = maxValue;
    }
    public int getStep() {
        return step;
    }
    public void setStep(int step) {
        this.step = step;
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + maxValue;
        result = prime * result + minValue;
        result = prime * result + step;
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
        IntegerConstraints other = (IntegerConstraints) obj;
        if (maxValue != other.maxValue)
            return false;
        if (minValue != other.minValue)
            return false;
        if (step != other.step)
            return false;
        return true;
    }
    @Override
    public String toString() {
        return "IntegerConstraints [minValue=" + minValue + ", maxValue=" + maxValue + ", step=" + step
                + ", enumeration=" + enumeration + ", allowMultiple=" + allowMultiple + "]";
    }
}
