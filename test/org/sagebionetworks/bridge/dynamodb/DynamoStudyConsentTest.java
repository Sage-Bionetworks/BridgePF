package org.sagebionetworks.bridge.dynamodb;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class DynamoStudyConsentTest {

    @Test 
    public void equalsHashCode() {
        EqualsVerifier.forClass(DynamoStudyConsent1.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }
    
}
