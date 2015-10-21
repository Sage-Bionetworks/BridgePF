package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DynamoUserConsent3Test {
    @Test
    public void test() {
        String healthCode = "123";
        String studyKey = "456";
        
        DynamoUserConsent3 userConsent = new DynamoUserConsent3(healthCode, studyKey);
        
        assertEquals(healthCode + ":" + studyKey, userConsent.getHealthCodeStudy());
        assertEquals(healthCode, userConsent.getHealthCode());
        assertEquals(studyKey, userConsent.getStudyIdentifier());
    }

}
