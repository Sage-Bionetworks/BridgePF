package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

public class StringConstraints extends Constraints {

    private static EnumSet<UIHint> UI_HINTS = EnumSet.of(UIHint.MULTILINETEXT, UIHint.TEXTFIELD);
    
    private int minLength = 0;
    private int maxLength = 255;
    private String pattern;

    public StringConstraints() {
        setDataType(DataType.STRING);
    }
    
    @Override
    @JsonIgnore
    public EnumSet<UIHint> getSupportedHints() {
        return UI_HINTS;
    }
    public int getMinLength() {
        return minLength;
    }
    public void setMinLength(int minLength) {
        this.minLength = minLength;
    }
    public int getMaxLength() {
        return maxLength;
    }
    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getPattern() {
        return pattern;
    }
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + maxLength;
        result = prime * result + minLength;
        result = prime * result + ((pattern == null) ? 0 : pattern.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        StringConstraints other = (StringConstraints) obj;
        if (maxLength != other.maxLength)
            return false;
        if (minLength != other.minLength)
            return false;
        if (pattern == null) {
            if (other.pattern != null)
                return false;
        } else if (!pattern.equals(other.pattern))
            return false;
        return true;
    }
    @Override
    public String toString() {
        return "StringConstraints [minLength=" + minLength + ", maxLength=" + maxLength + ", pattern=" + pattern + "]";
    }
    
}
