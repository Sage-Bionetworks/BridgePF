package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DynamoUserConsent3Test {
    @Test
    public void test() {
        String healthCode = "123";
        String studyKey = "456";
        
        DynamoUserConsent3 userConsent = new DynamoUserConsent3();
        // This is the only unusual setter in the class.
        userConsent.setHealthCodeStudy(healthCode, studyKey);
        
        assertEquals(healthCode + ":" + studyKey, userConsent.getHealthCodeStudy());
        assertEquals(healthCode, userConsent.getHealthCode());
        assertEquals(studyKey, userConsent.getStudyIdentifier());
        
        // These do update the compound key (it doesn't work the other way).
        userConsent.setHealthCode("BBB");
        assertEquals("BBB:" + studyKey, userConsent.getHealthCodeStudy());
        assertEquals("BBB", userConsent.getHealthCode());
        assertEquals(studyKey, userConsent.getStudyIdentifier());
        
        userConsent.setStudyIdentifier("CCC");
        assertEquals("BBB:CCC", userConsent.getHealthCodeStudy());
        assertEquals("BBB", userConsent.getHealthCode());
        assertEquals("CCC", userConsent.getStudyIdentifier());
    }

}
