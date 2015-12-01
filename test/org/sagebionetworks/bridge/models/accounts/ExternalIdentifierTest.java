package org.sagebionetworks.bridge.models.accounts;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ExternalIdentifierTest {

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(ExternalIdentifier.class).allFieldsShouldBeUsed().verify();
    }
    
}
