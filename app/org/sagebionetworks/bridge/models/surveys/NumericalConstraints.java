package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

public class NumericalConstraints extends Constraints {
    
    private Unit unit;
    private Double minValue;
    private Double maxValue;
    private Double step;
    
    public NumericalConstraints() {
        setSupportedHints(EnumSet.of(UIHint.NUMBERFIELD, UIHint.SLIDER, UIHint.SELECT));
    }
    
    public Unit getUnit() {
        return unit;
    }
    public void setUnit(Unit unit) {
        this.unit = unit;
    }
    public Double getMinValue() {
        return minValue;
    }
    public void setMinValue(Double minValue) {
        this.minValue = minValue;
    }
    public Double getMaxValue() {
        return maxValue;
    }
    public void setMaxValue(Double maxValue) {
        this.maxValue = maxValue;
    }
    public Double getStep() {
        return step;
    }
    public void setStep(Double step) {
        this.step = step;
    }
}
