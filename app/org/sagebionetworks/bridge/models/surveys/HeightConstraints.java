package org.sagebionetworks.bridge.models.surveys;

import static org.sagebionetworks.bridge.models.surveys.UIHint.HEIGHT;

import java.util.EnumSet;

public class HeightConstraints extends Constraints {
    private boolean isForInfant;
    private Unit unit;

    public HeightConstraints() {
        setDataType(DataType.DECIMAL);
        setSupportedHints(EnumSet.of(HEIGHT));
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
