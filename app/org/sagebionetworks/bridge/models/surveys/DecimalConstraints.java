package org.sagebionetworks.bridge.models.surveys;

public class DecimalConstraints extends EnumerableConstraints {

    private float minValue;
    private float maxValue;
    private float step;
    private float precision;
    
    @Override
    public String getDataType() {
        return "decimal";
    }
    public float getMinValue() {
        return minValue;
    }
    public void setMinValue(float minValue) {
        this.minValue = minValue;
    }
    public float getMaxValue() {
        return maxValue;
    }
    public void setMaxValue(float maxValue) {
        this.maxValue = maxValue;
    }
    public float getStep() {
        return step;
    }
    public void setStep(float step) {
        this.step = step;
    }
    public float getPrecision() {
        return precision;
    }
    public void setPrecision(float precision) {
        this.precision = precision;
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Float.floatToIntBits(maxValue);
        result = prime * result + Float.floatToIntBits(minValue);
        result = prime * result + Float.floatToIntBits(precision);
        result = prime * result + Float.floatToIntBits(step);
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
        DecimalConstraints other = (DecimalConstraints) obj;
        if (Float.floatToIntBits(maxValue) != Float.floatToIntBits(other.maxValue))
            return false;
        if (Float.floatToIntBits(minValue) != Float.floatToIntBits(other.minValue))
            return false;
        if (Float.floatToIntBits(precision) != Float.floatToIntBits(other.precision))
            return false;
        if (Float.floatToIntBits(step) != Float.floatToIntBits(other.step))
            return false;
        return true;
    }
    @Override
    public String toString() {
        return "DecimalConstraints [minValue=" + minValue + ", maxValue=" + maxValue + ", step=" + step
                + ", precision=" + precision + ", enumeration=" + enumeration + ", allowMultiple=" + allowMultiple
                + "]";
    }
}
