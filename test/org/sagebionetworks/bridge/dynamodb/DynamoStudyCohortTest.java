package org.sagebionetworks.bridge.dynamodb;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class DynamoStudyCohortTest {

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(DynamoStudyCohort.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed()
                .verify();
    }
    
}
