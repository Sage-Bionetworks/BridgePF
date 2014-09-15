package org.sagebionetworks.bridge.models.surveys;

public enum UIHint {
    
    CHECKBOX(true),
    COMBOBOX(true),
    DATEPICKER(false),
    DATETIMEPICKER(false),
    LIST(true),
    MULTILINETEXT(false),
    NUMBERFIELD(false),
    RADIOBUTTON(true),
    SELECT(true),
    SLIDER(true),
    TEXTFIELD(false),
    TIMEPICKER(false),
    TOGGLE(true);
    
    private boolean requiresEnumeration;
    
    private UIHint(boolean requiresEnumeration) {
        this.requiresEnumeration = requiresEnumeration;
    }
    
    public boolean requiresEnumeration(String dataType) {
        // booleans do not need an enumeration to make sense with a checkbox or toggle (there'll be only one)
        if ("boolean".equals(dataType) && this == CHECKBOX || this == TOGGLE) {
            return false;
        }
        // non-numeric types that specify a slider, must also specify an enumeration
        if (!"integer".equals(dataType) && !"decimal".equals(dataType) && this == SLIDER) {
            return true;
        }
        return requiresEnumeration;
    }
    
}

