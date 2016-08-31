package org.sagebionetworks.bridge.models.surveys;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

public final class SurveyQuestionOption {

    private final String label;
    private final String detail;
    private final String value;
    private final Image image;
    
    @JsonCreator
    public SurveyQuestionOption(@JsonProperty("label") String label, @JsonProperty("detail") String detail,
        @JsonProperty("value") String value, @JsonProperty("image") Image image) {
        this.label = label;
        this.detail = detail;
        this.value = value;
        this.image = image;
    }
    
    public SurveyQuestionOption(String label) {
        this(label, null, label, null);
    }
    
    public String getLabel() {
        return label;
    }
    public String getDetail() {
        return detail;
    }
    public String getValue() {
        return StringUtils.isNotBlank(value) ? value : label;
    }
    public Image getImage() {
        return image;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hashCode(image);
        result = prime * result + Objects.hashCode(label);
        result = prime * result + Objects.hashCode(detail);
        result = prime * result + Objects.hashCode(value);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final SurveyQuestionOption that = (SurveyQuestionOption) obj;
        return Objects.equals(image, that.image) && Objects.equals(label, that.label)
                && Objects.equals(detail, that.detail) && Objects.equals(value, that.value);
    }

    @Override
    public String toString() {
        return String.format("SurveyQuestionOption [label=%s, detail=%s, value=%s, image=%s]", 
            label, detail, value, image);
    }
    
}
