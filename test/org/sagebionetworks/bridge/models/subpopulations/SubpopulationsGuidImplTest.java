package org.sagebionetworks.bridge.models.subpopulations;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class SubpopulationsGuidImplTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(SubpopulationGuidImpl.class).verify();
    }
    
    @Test
    public void testToString() {
        assertEquals("ABC", new SubpopulationGuidImpl("ABC").toString());
    }
}
