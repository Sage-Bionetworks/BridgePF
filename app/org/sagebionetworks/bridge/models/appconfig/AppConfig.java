package org.sagebionetworks.bridge.models.appconfig;

import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.dynamodb.DynamoAppConfig;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.schedules.ConfigReference;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as=DynamoAppConfig.class)
public interface AppConfig extends BridgeEntity {
    
    static AppConfig create() {
        return new DynamoAppConfig();
    }

    String getStudyId();
    void setStudyId(String studyId);
    
    String getLabel();
    void setLabel(String label);
    
    long getCreatedOn();
    void setCreatedOn(long createdOn);
    
    long getModifiedOn();
    void setModifiedOn(long modifiedOn);
    
    String getGuid();
    void setGuid(String guid);
    
    Criteria getCriteria();
    void setCriteria(Criteria criteria);
    
    JsonNode getClientData();
    void setClientData(JsonNode clientData);
    
    List<SurveyReference> getSurveyReferences();
    void setSurveyReferences(List<SurveyReference> references);

    List<SchemaReference> getSchemaReferences();
    void setSchemaReferences(List<SchemaReference> references);
    
    List<ConfigReference> getConfigReferences();
    void setConfigReferences(List<ConfigReference> references);
    
    Map<String,JsonNode> getConfigElements();
    void setConfigElements(Map<String,JsonNode> configElements);
    
    Long getVersion();
    void setVersion(Long versions);

    boolean isDeleted();
    void setDeleted(boolean deleted);
}
