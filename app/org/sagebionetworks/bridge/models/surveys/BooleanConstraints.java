package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

public class BooleanConstraints extends Constraints {
    
    public BooleanConstraints() {
        setDataType(DataType.BOOLEAN);
        setSupportedHints(EnumSet.of(UIHint.CHECKBOX, UIHint.TOGGLE));
    }
    
}
