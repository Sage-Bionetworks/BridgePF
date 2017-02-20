package org.sagebionetworks.bridge.models.schedules;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;

import org.sagebionetworks.bridge.BridgeUtils;

/** Compound activity, which can include any number of schemas and surveys. */
@JsonDeserialize(builder = CompoundActivity.Builder.class)
public final class CompoundActivity {
    private final List<SchemaReference> schemaList;
    private final List<SurveyReference> surveyList;
    private final String taskIdentifier;

    /** Private constructor, use builder to construct. */
    private CompoundActivity(List<SchemaReference> schemaList, List<SurveyReference> surveyList,
            String taskIdentifier) {
        this.schemaList = schemaList;
        this.surveyList = surveyList;
        this.taskIdentifier = taskIdentifier;
    }

    /**
     * Returns true if this compound activity is a reference. That is, if this compound activity contains no schemas
     * and no surveys and needs to be resolved using the task ID before it can be used.
     */
    @JsonIgnore
    public boolean isReference() {
        return schemaList.isEmpty() && surveyList.isEmpty();
    }

    /** List of references to schemas associated with this activity. */
    public List<SchemaReference> getSchemaList() {
        return schemaList;
    }

    /** List of references to surveys associated with this activity. */
    public List<SurveyReference> getSurveyList() {
        return surveyList;
    }

    /** Task identifier associated with this activity, as defined in the study. */
    public String getTaskIdentifier() {
        return taskIdentifier;
    }

    /** Comound activity builder. */
    public static class Builder {
        private List<SchemaReference> schemaList;
        private List<SurveyReference> surveyList;
        private String taskIdentifier;

        /** Copy constructor. */
        public Builder copyOf(CompoundActivity other) {
            this.schemaList = other.getSchemaList();
            this.surveyList = other.getSurveyList();
            this.taskIdentifier = other.getTaskIdentifier();
            return this;
        }

        /** @see #getSchemaList */
        public Builder withSchemaList(List<SchemaReference> schemaList) {
            this.schemaList = schemaList;
            return this;
        }

        /** @see #getSurveyList */
        public Builder withSurveyList(List<SurveyReference> surveyList) {
            this.surveyList = surveyList;
            return this;
        }

        /** @see #getTaskIdentifier */
        public Builder withTaskIdentifier(String taskIdentifier) {
            this.taskIdentifier = taskIdentifier;
            return this;
        }

        /**
         * Builds a compound activity. This creates a defensive immutable copy of the schema list and survey list. If
         * there is no schema or survey list, it replaces it with an empty list.
         */
        public CompoundActivity build() {
            List<SchemaReference> schemaListCopy;
            if (schemaList != null) {
                schemaListCopy = ImmutableList.copyOf(schemaList);
            } else {
                schemaListCopy = ImmutableList.of();
            }

            List<SurveyReference> surveyListCopy;
            if (surveyList != null) {
                surveyListCopy = ImmutableList.copyOf(surveyList);
            } else {
                surveyListCopy = ImmutableList.of();
            }

            return new CompoundActivity(schemaListCopy, surveyListCopy, taskIdentifier);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CompoundActivity that = (CompoundActivity) o;
        return Objects.equals(schemaList, that.schemaList) &&
                Objects.equals(surveyList, that.surveyList) &&
                Objects.equals(taskIdentifier, that.taskIdentifier);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(schemaList, surveyList, taskIdentifier);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "CompoundActivity{" +
                "schemaList=[" + BridgeUtils.COMMA_SPACE_JOINER.join(schemaList) +
                "], surveyList=[" + BridgeUtils.COMMA_SPACE_JOINER.join(surveyList) +
                "], taskIdentifier='" + taskIdentifier + '\'' +
                '}';
    }
}
