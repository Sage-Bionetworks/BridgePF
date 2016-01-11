package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ScheduleCriteriaTest {
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(ScheduleCriteria.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void setsAreNeverNull() {
        ScheduleCriteria criteria = new ScheduleCriteria.Builder().withMaxAppVersion(2).withMaxAppVersion(10).build();
        
        assertTrue(criteria.getAllOfGroups().isEmpty());
        assertTrue(criteria.getNoneOfGroups().isEmpty());
        
        // Passing nulls into the builder's add* methods... this is always a mistake and should throw a NPE.
    }
}
