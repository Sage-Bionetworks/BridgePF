package org.sagebionetworks.bridge.models;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.junit.Test;

public class GuidCreatedOnVersionHolderImplTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(GuidCreatedOnVersionHolderImpl.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }
    
}
