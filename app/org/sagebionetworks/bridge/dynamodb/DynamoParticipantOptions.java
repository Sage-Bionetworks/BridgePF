package org.sagebionetworks.bridge.dynamodb;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Maps;

@DynamoDBTable(tableName = "ParticipantOptions")
public class DynamoParticipantOptions { 
    
    private String healthCode; // hash
    private String studyKey; // range
    private Map<String,String> options = Maps.newHashMap();
    
    @DynamoDBAttribute
    public String getStudyKey() {
        return studyKey;
    }
    public void setStudyKey(String studyKey) {
        this.studyKey = studyKey;
    }
    @DynamoDBHashKey(attributeName="healthDataCode")
    public String getHealthCode() {
        return healthCode;
    }
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }
    @DynamoDBIgnore
    public Map<String,String> getOptions() {
        return options;
    }
    public void setOptions(Map<String,String> options) {
        this.options = options;
    }
    @DynamoDBAttribute
    public String getData() {
        try {
            return BridgeObjectMapper.get().writeValueAsString(options);
        } catch(JsonProcessingException e) {
            throw new BridgeServiceException(e);
        }
    }
    public void setData(String data) {
        try {
            TypeReference<HashMap<String,Object>> typeRef = new TypeReference<HashMap<String,Object>>() {};
            options = BridgeObjectMapper.get().readValue(data, typeRef);
        } catch (IOException e) {
            throw new BridgeServiceException(e);
        }
    }

}
