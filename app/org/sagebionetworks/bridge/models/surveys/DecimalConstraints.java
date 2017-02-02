package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

public class DecimalConstraints extends NumericalConstraints {
    
    public DecimalConstraints() {
        setDataType(DataType.DECIMAL);
        setSupportedHints(EnumSet.of(UIHint.NUMBERFIELD, UIHint.SLIDER, UIHint.WEIGHT, UIHint.HEIGHT));
    }
    
}
