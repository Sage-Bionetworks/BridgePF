package org.sagebionetworks.bridge.hibernate;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

public class HibernateAccountConsentKeyTest {
    @Test
    public void equalsVerified() {
        EqualsVerifier.forClass(HibernateAccountConsentKey.class).allFieldsShouldBeUsed()
                .suppress(Warning.NONFINAL_FIELDS).verify();
    }
}
