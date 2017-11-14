package org.sagebionetworks.bridge.models.studies;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class AndroidAppLinkTest {
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(AndroidAppLink.class).allFieldsShouldBeUsed().verify();
    }
}
