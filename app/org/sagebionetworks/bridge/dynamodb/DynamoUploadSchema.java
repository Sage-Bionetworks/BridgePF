package org.sagebionetworks.bridge.dynamodb;

import java.io.IOException;
import java.util.List;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMappingException;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshaller;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadSchema;

@DynamoDBTable(tableName = "UploadSchema")
public class DynamoUploadSchema implements DynamoTable, UploadSchema {
    private List<UploadFieldDefinition> fieldDefList;
    private String name;
    private int rev;
    private String schemaId;
    private String studyId;

    @DynamoDBMarshalling(marshallerClass = FieldDefinitionListMarshaller.class)
    @Override
    public List<UploadFieldDefinition> getFieldDefinitions() {
        return fieldDefList;
    }

    public void setFieldDefinitions(List<UploadFieldDefinition> fieldDefList) {
        this.fieldDefList = fieldDefList;
    }

    @DynamoDBHashKey
    @JsonIgnore
    public String getKey() {
        return String.format("%s-%s", studyId, schemaId);
    }

    @JsonIgnore
    public void setKey(String key) {
        String[] parts = key.split("-", 2);
        // TODO: validate parts
        this.studyId = parts[0];
        this.schemaId = parts[1];
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

    @DynamoDBIgnore
    @Override
    public String getSchemaId() {
        return schemaId;
    }

    public void setSchemaId(String schemaId) {
        this.schemaId = schemaId;
    }

    // TODO: Implement global secondary indices in DynamoInitializer
    @DynamoDBIndexHashKey(attributeName = "studyId", globalSecondaryIndexName = "studyId-index")
    @Override
    public String getStudyId() {
        return studyId;
    }

    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }

    public static class FieldDefinitionListMarshaller implements DynamoDBMarshaller<List<UploadFieldDefinition>> {
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
