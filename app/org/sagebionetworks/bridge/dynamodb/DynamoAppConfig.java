package org.sagebionetworks.bridge.dynamodb;

import java.util.ArrayList;
import java.util.List;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedJson;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

@DynamoDBTable(tableName = "AppConfig")
@BridgeTypeName("AppConfig")
public class DynamoAppConfig implements AppConfig {

    private String studyId;
    private String guid;
    private Criteria criteria;
    private JsonNode clientData;
    private List<SurveyReference> surveyReferences;
    private List<SchemaReference> schemaReferences;
    private Long version;
    
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
    
    @DynamoDBTypeConvertedJson
    @Override
    public List<SurveyReference> getSurveyReferences() {
        if (surveyReferences == null) {
            surveyReferences = new ArrayList<>();
        }
        return surveyReferences;
    }

    @Override
    public void setSurveyReferences(List<SurveyReference> references) {
        this.surveyReferences = references; 
    }

    @DynamoDBTypeConvertedJson
    @Override
    public List<SchemaReference> getSchemaReferences() {
        if (schemaReferences == null) {
            schemaReferences = new ArrayList<>();
        }
        return schemaReferences;
    }

    @Override
    public void setSchemaReferences(List<SchemaReference> references) {
        this.schemaReferences = references;
    }
    
    @DynamoDBVersionAttribute
    @Override
    public Long getVersion() {
        return version;
    }
    
    @Override
    public void setVersion(Long version) {
        this.version = version;
    }

}
