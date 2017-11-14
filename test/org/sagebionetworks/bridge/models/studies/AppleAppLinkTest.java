package org.sagebionetworks.bridge.models.studies;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class AppleAppLinkTest {
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(AppleAppLink.class).allFieldsShouldBeUsed().verify();
    }
}
