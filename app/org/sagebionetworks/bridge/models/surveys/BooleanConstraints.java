package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class BooleanConstraints extends Constraints {
    
    private static EnumSet<UIHint> UI_HINTS = EnumSet.of(UIHint.CHECKBOX, UIHint.TOGGLE);
    
    public BooleanConstraints() {
        setDataType(DataType.BOOLEAN);
    }
    
    @Override
    @JsonIgnore
    public EnumSet<UIHint> getSuportedHints() {
        return UI_HINTS;
    }

}
