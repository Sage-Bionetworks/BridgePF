package org.sagebionetworks.bridge.models.substudies;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class AccountSubstudyIdTest {

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(AccountSubstudyId.class).allFieldsShouldBeUsed().suppress(Warning.NONFINAL_FIELDS)
                .verify();
    }
    
    @Test
    public void create() { 
        AccountSubstudyId key = new AccountSubstudyId("studyId", "substudyId", "accountId");
        assertEquals("studyId", key.getStudyId());
        assertEquals("substudyId", key.getSubstudyId());
        assertEquals("accountId", key.getAccountId());
    }
    
}
