package org.sagebionetworks.bridge.models.surveys;

public enum UIHint {
    
    // Common form controls across platforms
    CHECKBOX,
    COMBOBOX,
    DATEPICKER,
    DATETIMEPICKER,
    LIST,
    MULTILINETEXT,
    NUMBERFIELD,
    RADIOBUTTON,
    SELECT,
    SLIDER,
    TEXTFIELD,
    TIMEPICKER,
    TOGGLE,
    // Some common medical measurements for which we have custom, one-screen UIs
    BLOODPRESSURE,
    HEIGHT,
    WEIGHT;
}
