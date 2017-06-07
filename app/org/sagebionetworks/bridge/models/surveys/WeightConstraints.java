package org.sagebionetworks.bridge.models.surveys;

import static org.sagebionetworks.bridge.models.surveys.UIHint.WEIGHT;

import java.util.EnumSet;

public class WeightConstraints extends NumericalConstraints {
    private boolean isForInfant;

    public WeightConstraints() {
        super();
        setDataType(DataType.WEIGHT);
        setSupportedHints(EnumSet.of(WEIGHT));
    }

    public void setForInfant(boolean isForInfant) {
        this.isForInfant = isForInfant;
    }

    public boolean isForInfant() {
        return isForInfant;
    }
}
