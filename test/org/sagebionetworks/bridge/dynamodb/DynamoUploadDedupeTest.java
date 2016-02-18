package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DynamoUploadDedupeTest {
    @Test
    public void getDdbKeyFromHealthCodeAndMd5() {
        DynamoUploadDedupe dedupe = new DynamoUploadDedupe();
        dedupe.setHealthCode("test-healthcode");
        dedupe.setUploadMd5("test-md5");
        assertEquals("test-healthcode:test-md5", dedupe.getDdbKey());
    }

    @Test
    public void getHealthCodeAndMd5FromDdbKey() {
        DynamoUploadDedupe dedupe = new DynamoUploadDedupe();
        dedupe.setDdbKey("test-healthcode:test-md5");
        assertEquals("test-healthcode", dedupe.getHealthCode());
        assertEquals("test-md5", dedupe.getUploadMd5());
    }
}
