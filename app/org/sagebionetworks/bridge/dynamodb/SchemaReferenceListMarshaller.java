package org.sagebionetworks.bridge.dynamodb;

import java.io.IOException;
import java.util.List;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

public class SchemaReferenceListMarshaller implements DynamoDBTypeConverter<String,List<SchemaReference>> {

    private static final TypeReference<List<SchemaReference>> TYPE_REF = new TypeReference<List<SchemaReference>>() {};
    
    @Override
    public String convert(List<SchemaReference> list) {
        try {
            return BridgeObjectMapper.get().writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new BridgeServiceException(e);
        }
    }

    @Override
    public List<SchemaReference> unconvert(String string) {
        try {
            return BridgeObjectMapper.get().readValue(string, TYPE_REF);
        } catch (IOException e) {
            throw new BridgeServiceException(e);
        }
    }

}

