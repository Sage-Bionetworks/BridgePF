package org.sagebionetworks.bridge.models.substudies;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SubstudyIdTest {
    @Test
    public void test() {
        SubstudyId studyId = new SubstudyId("studyId", "id");
        
        assertEquals("studyId", studyId.getStudyId());
        assertEquals("id", studyId.getId());
    }    
}
