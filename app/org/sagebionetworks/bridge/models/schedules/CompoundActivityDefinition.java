package org.sagebionetworks.bridge.models.schedules;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

import org.sagebionetworks.bridge.dynamodb.DynamoCompoundActivityDefinition;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

/**
 * Compound activity definition, which is keyed off taskID and stored in persistent storage. This allows study
 * developers to define a task once and use that task in multiple schedules.
 */
@BridgeTypeName("CompoundActivityDefinition")
@JsonDeserialize(as = DynamoCompoundActivityDefinition.class)
public interface CompoundActivityDefinition extends BridgeEntity {
    ObjectWriter PUBLIC_DEFINITION_WRITER = BridgeObjectMapper.get().writer(new SimpleFilterProvider().addFilter("filter",
            SimpleBeanPropertyFilter.serializeAllExcept("studyId")));

    /** Convenience method for creating a CompoundActivityDefinition using a concrete implementation. */
    static CompoundActivityDefinition create() {
        return new DynamoCompoundActivityDefinition();
    }

    /** Helper method which constructs a Compound Activity instance from its definition. */
    @JsonIgnore
    default CompoundActivity getCompoundActivity() {
        return new CompoundActivity.Builder().withSchemaList(getSchemaList()).withSurveyList(getSurveyList())
                .withTaskIdentifier(getTaskId()).build();
    }

    /** List of schemas in this activity definition. */
    List<SchemaReference> getSchemaList();

    /** @see #getSchemaList */
    void setSchemaList(List<SchemaReference> schemaList);

    /**
     * Compound activity definitions (and task identifiers) are namespaced to studies. As such, it's important for a
     * definition to know its study.
     */
    String getStudyId();

    /** @see #getStudyId */
    void setStudyId(String studyId);

    /** List of surveys in this activity definition. */
    List<SurveyReference> getSurveyList();

    /** @see #getSurveyList */
    void setSurveyList(List<SurveyReference> surveyList);

    /** Task ID. This is the primary key of the compound activity definition. */
    String getTaskId();

    /** @see #getTaskId */
    void setTaskId(String taskId);
}
