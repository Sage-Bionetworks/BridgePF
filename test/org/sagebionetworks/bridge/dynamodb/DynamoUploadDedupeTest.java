package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DynamoUploadDedupeTest {
    @Test
    public void getDdbKeyFromHealthCodeAndSchema() {
        DynamoUploadDedupe dedupe = new DynamoUploadDedupe();
        dedupe.setHealthCode("test-healthcode");
        dedupe.setSchemaKey("test-schema");
        assertEquals("test-healthcode:test-schema", dedupe.getDdbKey());
    }

    @Test
    public void getHealthCodeAndSchemaFromDdbKey() {
        DynamoUploadDedupe dedupe = new DynamoUploadDedupe();
        dedupe.setDdbKey("test-healthcode:test-schema");
        assertEquals("test-healthcode", dedupe.getHealthCode());
        assertEquals("test-schema", dedupe.getSchemaKey());
    }
}
