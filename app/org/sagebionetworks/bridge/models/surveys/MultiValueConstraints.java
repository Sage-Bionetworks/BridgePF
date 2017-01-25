package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;
import java.util.List;

/**
 * The only way to constrain a multiple value answer is through an enumeration of the 
 * values that are allowed. However, an enumeration is not required. This should have 
 * been called EnumerationConstraint.
 * 
 * Note that if a user can enter an "other" value, even if there is an enumeration of 
 * the allowable values, then there will be no validation on the submitted answer, 
 * except to verify that it is the right data type.
 */
public class MultiValueConstraints extends Constraints {

    /**
     * These UI hints represent controls that will only accept one value, so they shouldn't be declared
     * when allowMultiple == true.
     */
    public static final EnumSet<UIHint> ONE_ONLY = EnumSet.of(UIHint.COMBOBOX, UIHint.RADIOBUTTON, UIHint.SELECT, UIHint.SLIDER);
    
    /**
     * These UI hints represent controls that will accept multiple values, so they shouldn't be declared
     * when allowMultiple == false.
     */
    public static final EnumSet<UIHint> MANY_ONLY = EnumSet.of(UIHint.CHECKBOX);
    
    /**
     * These UI hints represent controls that really require the ability to enter an "other" value. 
     * For other hints, if allowOther == true, you would need additional controls to implement. 
     */
    public static final EnumSet<UIHint> OTHER_ALWAYS_ALLOWED = EnumSet.of(UIHint.COMBOBOX);
    
    private List<SurveyQuestionOption> enumeration;
    private boolean allowOther = false;
    private boolean allowMultiple = false;
    
    public MultiValueConstraints() {
        this(DataType.STRING);
    }
    public MultiValueConstraints(DataType dataType) {
        setDataType(dataType);
        setSupportedHints(EnumSet.of(UIHint.CHECKBOX, UIHint.COMBOBOX, UIHint.LIST, UIHint.RADIOBUTTON, UIHint.SELECT,
                UIHint.SLIDER));
    }
    
    public List<SurveyQuestionOption> getEnumeration() {
        return enumeration;
    }
    public void setEnumeration(List<SurveyQuestionOption> enumeration) {
        this.enumeration = enumeration;
    }
    public boolean getAllowOther() {
        return allowOther;
    }
    public void setAllowOther(boolean allowOther) {
        this.allowOther = allowOther;
    }
    public boolean getAllowMultiple() {
        return allowMultiple;
    }
    public void setAllowMultiple(boolean allowMultiple) {
        this.allowMultiple = allowMultiple;
    }
}
