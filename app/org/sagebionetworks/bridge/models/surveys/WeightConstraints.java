package org.sagebionetworks.bridge.models.surveys;

import static org.sagebionetworks.bridge.models.surveys.UIHint.WEIGHT;

import java.util.EnumSet;

public class WeightConstraints extends DecimalConstraints {
    private boolean isForInfant;

    public WeightConstraints() {
        super();
    }

    public void setIsForInfant(boolean isForInfant) {
        this.isForInfant = isForInfant;
    }

    public boolean isForInfant() {
        return isForInfant;
    }
}
