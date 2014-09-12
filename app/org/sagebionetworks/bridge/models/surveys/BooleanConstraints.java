package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class BooleanConstraints extends EnumerableConstraints {
    
    private static EnumSet<UIHint> UI_HINTS = EnumSet.of(UIHint.CHECKBOX, UIHint.TOGGLE);
    
    @Override
    public String getDataType() {
        return "boolean";
    }

    @Override
    @JsonIgnore
    public EnumSet<UIHint> getSuportedHints() {
        return UI_HINTS;
    }

}
