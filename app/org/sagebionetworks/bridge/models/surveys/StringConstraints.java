package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

import com.fasterxml.jackson.annotation.JsonInclude;

public class StringConstraints extends Constraints {

    private Integer minLength;
    private Integer maxLength;
    private String pattern;

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
        result = prime * result + ((maxLength == null) ? 0 : maxLength.hashCode());
        result = prime * result + ((minLength == null) ? 0 : minLength.hashCode());
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
        if (maxLength == null) {
            if (other.maxLength != null)
                return false;
        } else if (!maxLength.equals(other.maxLength))
            return false;
        if (minLength == null) {
            if (other.minLength != null)
                return false;
        } else if (!minLength.equals(other.minLength))
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
