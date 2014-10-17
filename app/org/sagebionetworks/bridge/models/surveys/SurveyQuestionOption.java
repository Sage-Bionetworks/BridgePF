package org.sagebionetworks.bridge.models.surveys;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SurveyQuestionOption {

    private final String label;
    private final String value;
    private final Image image;
    
    @JsonCreator
    public SurveyQuestionOption(@JsonProperty("label") String label, @JsonProperty("value") String value,
            @JsonProperty("image") Image image) {
        this.label = label;
        this.value = value;
        this.image = image;
    }
    
    public String getLabel() {
        return label;
    }
    public String getValue() {
        return value;
    }
    public Image getImage() {
        return image;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((image == null) ? 0 : image.hashCode());
        result = prime * result + ((label == null) ? 0 : label.hashCode());
        result = prime * result + ((value == null) ? 0 : value.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SurveyQuestionOption other = (SurveyQuestionOption) obj;
        if (image == null) {
            if (other.image != null)
                return false;
        } else if (!image.equals(other.image))
            return false;
        if (label == null) {
            if (other.label != null)
                return false;
        } else if (!label.equals(other.label))
            return false;
        if (value == null) {
            if (other.value != null)
                return false;
        } else if (!value.equals(other.value))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "SurveyQuestionOption [label=" + label + ", value=" + value + ", image=" + image + "]";
    }
    
}
