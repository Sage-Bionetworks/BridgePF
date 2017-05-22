package org.sagebionetworks.bridge.models.surveys;

import static org.sagebionetworks.bridge.models.surveys.UIHint.WEIGHT;

import java.util.EnumSet;

public class WeightConstraints extends Constraints {
    private boolean isForInfant;
    private Unit unit;

    public WeightConstraints() {
        setDataType(DataType.DECIMAL);
        setSupportedHints(EnumSet.of(WEIGHT));
    }

    public void setIsForInfant(boolean isForInfant) {
        this.isForInfant = isForInfant;
    }

    public boolean isForInfant() {
        return isForInfant;
    }

    public Unit getUnit() {
        return unit;
    }

    public void setUnit(Unit unit) {
        this.unit = unit;
    }
}
