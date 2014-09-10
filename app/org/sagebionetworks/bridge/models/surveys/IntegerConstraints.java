package org.sagebionetworks.bridge.models.surveys;


public class IntegerConstraints extends Constraints {

    private int minValue;
    private int maxValue;
    
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
    
}
