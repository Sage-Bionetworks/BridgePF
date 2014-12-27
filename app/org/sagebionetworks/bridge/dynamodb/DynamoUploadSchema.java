package org.sagebionetworks.bridge.dynamodb;

import java.io.IOException;
import java.util.List;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMappingException;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshaller;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadSchema;

@BridgeTypeName("UploadSchema")
@DynamoDBTable(tableName = "UploadSchema")
public class DynamoUploadSchema implements UploadSchema {
    private List<UploadFieldDefinition> fieldDefList;
    private String id;
    private String name;
    private int rev;

    @DynamoDBMarshalling(marshallerClass = FieldDefinitionListMarshaller.class)
    @Override
    public List<UploadFieldDefinition> getFieldDefinitions() {
        return fieldDefList;
    }

    public void setFieldDefinitions(List<UploadFieldDefinition> fieldDefList) {
        this.fieldDefList = fieldDefList;
    }

    @DynamoDBHashKey
    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // We don't use the DynamoDBVersionAttribute here because we want to keep multiple versions of the schema so we can
    // parse older versions of the data. Similarly, we make this a range key so that we can always find the latest
    // version of the schema.
    @DynamoDBRangeKey
    @Override
    public int getRevision() {
        return rev;
    }

    public void setRevision(int rev) {
        this.rev = rev;
    }

    private static class FieldDefinitionListMarshaller implements DynamoDBMarshaller<List<UploadFieldDefinition>> {
        private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();

        @Override
        public String marshall(List<UploadFieldDefinition> fieldDefList) {
            try {
                return JSON_OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(fieldDefList);
            } catch (JsonProcessingException ex) {
                throw new DynamoDBMappingException(ex);
            }
        }

        @Override
        public List<UploadFieldDefinition> unmarshall(Class<List<UploadFieldDefinition>> clazz, String json) {
            try {
                return JSON_OBJECT_MAPPER.readValue(json, new TypeReference<List<UploadFieldDefinition>>() {});
            } catch (IOException ex) {
                throw new DynamoDBMappingException(ex);
            }
        }
    }
}
