package org.sagebionetworks.bridge.dynamodb;

import java.util.List;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;

import org.sagebionetworks.bridge.models.schedules.CompoundActivityDefinition;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;

/**
 * DynamoDB implementation of CompoundActivityDefinition. The hash key is study ID and the range key is the task ID.
 * This allows us to namespace compound activity definitions to studies and to list all definitions in a study.
 */
@DynamoDBTable(tableName = "CompoundActivityDefinition")
@JsonFilter("filter")
public class DynamoCompoundActivityDefinition implements CompoundActivityDefinition {
    private List<SchemaReference> schemaList = ImmutableList.of();
    private String studyId;
    private List<SurveyReference> surveyList = ImmutableList.of();
    private String taskId;
    private Long version;

    /** {@inheritDoc} */
    @DynamoDBTypeConverted(converter = SchemaReferenceListMarshaller.class)
    @Override
    public List<SchemaReference> getSchemaList() {
        return schemaList;
    }

    /** @see #getSchemaList */
    @Override
    public void setSchemaList(List<SchemaReference> schemaList) {
        this.schemaList = schemaList != null ? ImmutableList.copyOf(schemaList) : ImmutableList.of();
    }

    /** {@inheritDoc} */
    @DynamoDBHashKey
    @Override
    public String getStudyId() {
        return studyId;
    }

    /** @see #getStudyId */
    @Override
    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }

    /** {@inheritDoc} */
    @DynamoDBTypeConverted(converter = SurveyReferenceListMarshaller.class)
    @Override
    public List<SurveyReference> getSurveyList() {
        return surveyList;
    }

    /** @see #getSurveyList */
    @Override
    public void setSurveyList(List<SurveyReference> surveyList) {
        this.surveyList = surveyList != null ? ImmutableList.copyOf(surveyList) : ImmutableList.of();
    }

    /** {@inheritDoc} */
    @DynamoDBRangeKey
    public String getTaskId() {
        return taskId;
    }

    /** @see #getTaskId */
    @Override
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    /** For use with DynamoDB versioning and concurrency. */
    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }

    /** @see #getVersion */
    public void setVersion(Long version) {
        this.version = version;
    }

    /** DynamoDB annotations don't work with generics, so we need to subclass ListMarshaller. */
    public static class SchemaReferenceListMarshaller extends ListMarshaller<SchemaReference> {
        private static final TypeReference<List<SchemaReference>> SCHEMA_REF_LIST_TYPE =
                new TypeReference<List<SchemaReference>>() {};

        /** {@inheritDoc} */
        @Override
        public TypeReference<List<SchemaReference>> getTypeReference() {
            return SCHEMA_REF_LIST_TYPE;
        }
    }

    /** Similarly for surveys. */
    public static class SurveyReferenceListMarshaller extends ListMarshaller<SurveyReference> {
        private static final TypeReference<List<SurveyReference>> SURVEY_REF_LIST_TYPE =
                new TypeReference<List<SurveyReference>>() {};

        /** {@inheritDoc} */
        @Override
        public TypeReference<List<SurveyReference>> getTypeReference() {
            return SURVEY_REF_LIST_TYPE;
        }
    }
}
