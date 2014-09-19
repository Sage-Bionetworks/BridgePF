package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

import org.sagebionetworks.bridge.validators.Messages;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class IntegerConstraints extends Constraints {

    private static EnumSet<UIHint> UI_HINTS = EnumSet.of(UIHint.NUMBERFIELD, UIHint.SLIDER);
    
    private Long minValue;
    private Long maxValue;
    private Long step;
    
    public IntegerConstraints() {
        setDataType(DataType.INTEGER);
    }
    
    @Override
    @JsonIgnore
    public EnumSet<UIHint> getSupportedHints() {
        return UI_HINTS;
    }
    public Long getMinValue() {
        return minValue;
    }
    public void setMinValue(Long minValue) {
        this.minValue = minValue;
    }
    public Long getMaxValue() {
        return maxValue;
    }
    public void setMaxValue(Long maxValue) {
        this.maxValue = maxValue;
    }
    public Long getStep() {
        return step;
    }
    public void setStep(Long step) {
        this.step = step;
    }
    public void validate(Messages messages, SurveyAnswer answer) {
        long value = (Long)answer.getAnswer();
        if (minValue != null && value < minValue) {
            messages.add("it is lower than the minimum value of %s", minValue);
        }
        if (maxValue != null && value > maxValue) {
            messages.add("it is higher than the maximum value of %s", maxValue);
        }
        if (step != null && value % step != 0) {
            messages.add("it is not a step value of %s", step);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((maxValue == null) ? 0 : maxValue.hashCode());
        result = prime * result + ((minValue == null) ? 0 : minValue.hashCode());
        result = prime * result + ((step == null) ? 0 : step.hashCode());
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
        if (maxValue == null) {
            if (other.maxValue != null)
                return false;
        } else if (!maxValue.equals(other.maxValue))
            return false;
        if (minValue == null) {
            if (other.minValue != null)
                return false;
        } else if (!minValue.equals(other.minValue))
            return false;
        if (step == null) {
            if (other.step != null)
                return false;
        } else if (!step.equals(other.step))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "IntegerConstraints [minValue=" + minValue + ", maxValue=" + maxValue + ", step=" + step + "]";
    }
}
