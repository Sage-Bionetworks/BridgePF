package org.sagebionetworks.bridge.models.subpopulations;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class SubpopulationGuidTest {
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(SubpopulationGuid.class).verify();
    }
    @Test
    public void testToString() {
        assertEquals("ABC", SubpopulationGuid.create("ABC").toString());
    }
}
