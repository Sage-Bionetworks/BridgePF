package org.sagebionetworks.bridge.models.surveys;

import static org.sagebionetworks.bridge.models.surveys.UIHint.HEIGHT;

import java.util.EnumSet;

public class HeightConstraints extends NumericalConstraints {
    private boolean isForInfant;
    private Unit unit;

    public HeightConstraints() {
        super();
        setDataType(DataType.HEIGHT);
    }

    public void setIsForInfant(boolean isForInfant) {
        this.isForInfant = isForInfant;
    }

    public boolean isForInfant() {
        return isForInfant;
    }
}
