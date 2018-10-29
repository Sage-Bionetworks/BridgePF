package org.sagebionetworks.bridge.dynamodb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.sagebionetworks.bridge.dynamodb.DynamoCompoundActivityDefinition.SchemaReferenceListMarshaller;
import org.sagebionetworks.bridge.dynamodb.DynamoCompoundActivityDefinition.SurveyReferenceListMarshaller;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.json.DateTimeToLongDeserializer;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.schedules.ConfigReference;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@DynamoDBTable(tableName = "AppConfig")
@BridgeTypeName("AppConfig")
public class DynamoAppConfig implements AppConfig {
    public static class ConfigReferenceListMarshaller extends ListMarshaller<ConfigReference> {
        private static final TypeReference<List<ConfigReference>> CONFIG_REF_LIST_TYPE =
                new TypeReference<List<ConfigReference>>() {};

        /** {@inheritDoc} */
        @Override
        public TypeReference<List<ConfigReference>> getTypeReference() {
            return CONFIG_REF_LIST_TYPE;
        }
    }

    private String studyId;
    private String label;
    private String guid;
    private Criteria criteria;
    private long createdOn;
    private long modifiedOn;
    private JsonNode clientData;
    private List<SurveyReference> surveyReferences;
    private List<SchemaReference> schemaReferences;
    private List<ConfigReference> configReferences;
    boolean configIncluded;
    private Map<String,JsonNode> configElements;
    private Long version;
    private boolean deleted;
    
    @JsonIgnore
    @DynamoDBHashKey
    @Override
    public String getStudyId() {
        return studyId;
    }

    @Override
    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }
    
    @Override
    public String getLabel() {
        return label;
    }
    
    @Override
    public void setLabel(String label) {
        this.label = label;
    }
    
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @Override
    public long getCreatedOn() {
        return createdOn;
    }
    
    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    @Override
    public void setCreatedOn(long createdOn) {
        this.createdOn = createdOn;
    }
    
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @Override
    public long getModifiedOn() {
        return modifiedOn;
    }
    
    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    @Override
    public void setModifiedOn(long modifiedOn) {
        this.modifiedOn = modifiedOn;
    }
    
    @DynamoDBRangeKey
    @Override
    public String getGuid() {
        return guid;
    }

    @Override
    public void setGuid(String guid) {
        this.guid = guid;
    }
    
    @DynamoDBIgnore
    @Override
    public Criteria getCriteria() {
        return criteria;
    }

    @Override
    public void setCriteria(Criteria criteria) {
        this.criteria = criteria;
    }

    @DynamoDBTypeConverted(converter = JsonNodeMarshaller.class)
    @DynamoDBAttribute
    @Override
    public JsonNode getClientData() {
        return clientData;
    }

    @DynamoDBTypeConverted(converter = JsonNodeMarshaller.class)
    @Override
    public void setClientData(JsonNode clientData) {
        this.clientData = clientData;
    }
    
    @DynamoDBTypeConverted(converter=SurveyReferenceListMarshaller.class)
    @Override
    public List<SurveyReference> getSurveyReferences() {
        if (surveyReferences == null) {
            surveyReferences = new ArrayList<>();
        }
        return surveyReferences;
    }

    @DynamoDBTypeConverted(converter=SurveyReferenceListMarshaller.class)
    @Override
    public void setSurveyReferences(List<SurveyReference> references) {
        this.surveyReferences = references; 
    }

    @DynamoDBTypeConverted(converter=SchemaReferenceListMarshaller.class)
    @Override
    public List<SchemaReference> getSchemaReferences() {
        if (schemaReferences == null) {
            schemaReferences = new ArrayList<>();
        }
        return schemaReferences;
    }

    @DynamoDBTypeConverted(converter=SchemaReferenceListMarshaller.class)
    @Override
    public void setSchemaReferences(List<SchemaReference> references) {
        this.schemaReferences = references;
    }
    
    @DynamoDBTypeConverted(converter=ConfigReferenceListMarshaller.class)
    @Override
    public List<ConfigReference> getConfigReferences() {
        if (configReferences == null) {
            configReferences = new ArrayList<>();
        }
        return configReferences;
    }
    
    @DynamoDBTypeConverted(converter=ConfigReferenceListMarshaller.class)
    @Override
    public void setConfigReferences(List<ConfigReference> references) {
        this.configReferences = references;
    }
    
    @DynamoDBIgnore
    @Override
    public Map<String,JsonNode> getConfigElements() {
        if (configElements == null) {
            configElements = new HashMap<>();
        }
        return configElements;
    }
    
    @Override
    public void setConfigElements(Map<String,JsonNode> configElements) {
        this.configElements = configElements;
    };
        
    @DynamoDBVersionAttribute
    @Override
    public Long getVersion() {
        return version;
    }
    
    @Override
    public void setVersion(Long version) {
        this.version = version;
    }
    
    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientData, createdOn, criteria, guid, label, modifiedOn, schemaReferences, studyId,
                surveyReferences, version, deleted);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DynamoAppConfig other = (DynamoAppConfig) obj;
        return Objects.equals(clientData, other.clientData) && Objects.equals(createdOn, other.createdOn)
                && Objects.equals(criteria, other.criteria) && Objects.equals(guid, other.guid)
                && Objects.equals(label, other.label) && Objects.equals(modifiedOn, other.modifiedOn)
                && Objects.equals(getSchemaReferences(), other.getSchemaReferences())
                && Objects.equals(getSurveyReferences(), other.getSurveyReferences()) 
                && Objects.equals(studyId, other.studyId) && Objects.equals(version, other.version)
                && Objects.equals(deleted, other.deleted);
    }

    @Override
    public String toString() {
        return "DynamoAppConfig [studyId=" + studyId + ", label=" + label + ", guid=" + guid + ", criteria=" + criteria
                + ", createdOn=" + createdOn + ", modifiedOn=" + modifiedOn + ", clientData=" + clientData
                + ", surveyReferences=" + getSurveyReferences() + ", schemaReferences=" + getSchemaReferences()
                + ", version=" + version + ", deleted=" + deleted + "]";
    }
}
