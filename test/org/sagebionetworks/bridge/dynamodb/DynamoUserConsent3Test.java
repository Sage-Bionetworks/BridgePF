package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DynamoUserConsent3Test {
    @Test
    public void test() {
        DynamoUserConsent3 userConsent = new DynamoUserConsent3("123", "456");
        
        assertEquals("123:456", userConsent.getHealthCodeSubpopGuid());
        assertEquals("123", userConsent.getHealthCode());
        assertEquals("456", userConsent.getSubpopulationGuid());
        
        userConsent.setHealthCodeSubpopGuid("ABC:DEF");
        assertEquals("ABC", userConsent.getHealthCode());
        assertEquals("DEF", userConsent.getSubpopulationGuid());
    }

}
