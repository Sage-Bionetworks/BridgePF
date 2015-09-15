package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.upload.UploadSchema;

public class DynamoUploadSchemaTest {

    private static final String TEST_SCHEMA_JSON = "{" +
                    "   \"name\":\"Controller Test Schema\"," +
                    "   \"revision\":3," +
                    "   \"schemaId\":\"controller-test-schema\"," +
                    "   \"schemaType\":\"ios_data\"," +
                    "   \"fieldDefinitions\":[" +
                    "       {" +
                    "           \"name\":\"field-name\"," +
                    "           \"required\":true," +
                    "           \"type\":\"STRING\"" +
                    "       }" +
                    "   ]" +
                    "}";
    
    private static final String INVALID_TEST_SCHEMA_JSON = "{" +
                    "   \"revision\":3," +
                    "   \"schemaId\":\"controller-test-schema\"," +
                    "   \"fieldDefinitions\":[" +
                    "       {" +
                    "           \"required\":true" +
                    "       }" +
                    "   ]" +
                    "}";
    
    @Test
    public void canDeserializeJson() throws Exception {
        UploadSchema schema = BridgeObjectMapper.get().readValue(TEST_SCHEMA_JSON, UploadSchema.class);
        
        assertEquals("Controller Test Schema", schema.getName());
        assertEquals(3, schema.getRevision());
        assertEquals("controller-test-schema", schema.getSchemaId());
        assertEquals(1, schema.getFieldDefinitions().size());
    }
    
    @Test
    public void jsonWithMultipleErrorsCapturesAllErrors() throws Exception {
        try {
            BridgeObjectMapper.get().readValue(INVALID_TEST_SCHEMA_JSON, UploadSchema.class);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("name is required", e.getErrors().get("name").get(0));
            assertEquals("schemaType is required", e.getErrors().get("schemaType").get(0));
            assertEquals("fieldDefinitions0.name is required", e.getErrors().get("fieldDefinitions0.name").get(0));
            assertEquals("fieldDefinitions0.type is required", e.getErrors().get("fieldDefinitions0.type").get(0));
        }
    }
    
}
