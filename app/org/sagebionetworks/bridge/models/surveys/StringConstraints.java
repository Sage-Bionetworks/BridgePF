package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

public final class StringConstraints extends Constraints {

    private Integer minLength;
    private Integer maxLength;
    private String pattern;
    private String patternErrorMessage;
    private String patternPlaceholder;

    public StringConstraints() {
        setDataType(DataType.STRING);
        setSupportedHints(EnumSet.of(UIHint.MULTILINETEXT, UIHint.TEXTFIELD));
    }

    public Integer getMinLength() {
        return minLength;
    }

    public void setMinLength(Integer minLength) {
        this.minLength = minLength;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getPatternErrorMessage() {
        return patternErrorMessage;
    }

    public void setPatternErrorMessage(String patternErrorMessage) {
        this.patternErrorMessage = patternErrorMessage;
    }
    
    public String getPatternPlaceholder() {
        return patternPlaceholder;
    }

    public void setPatternPlaceholder(String patternPlaceholder) {
        this.patternPlaceholder = patternPlaceholder;
    }

    @Override
    public String toString() {
        return "StringConstraints [minLength=" + minLength + ", maxLength=" + maxLength + ", pattern=" + pattern
                + ", patternErrorMessage=" + patternErrorMessage + ", patternPlaceholder=" + patternPlaceholder + "]";
    }
}
