package org.sagebionetworks.bridge.models.substudies;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class SubstudyIdTest {
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(SubstudyId.class).allFieldsShouldBeUsed()
            .suppress(Warning.NONFINAL_FIELDS).verify();
    }
    
    @Test
    public void test() {
        SubstudyId studyId = new SubstudyId("studyId", "id");
        
        assertEquals("studyId", studyId.getStudyId());
        assertEquals("id", studyId.getId());
    }    
}
