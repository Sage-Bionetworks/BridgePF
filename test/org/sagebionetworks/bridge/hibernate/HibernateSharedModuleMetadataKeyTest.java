package org.sagebionetworks.bridge.hibernate;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

public class HibernateSharedModuleMetadataKeyTest {
    @Test
    public void equalsVerifier() {
        EqualsVerifier.forClass(HibernateSharedModuleMetadataKey.class).suppress(Warning.NONFINAL_FIELDS)
                .allFieldsShouldBeUsed().verify();
    }
}
