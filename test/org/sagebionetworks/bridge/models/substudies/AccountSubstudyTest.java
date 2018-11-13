package org.sagebionetworks.bridge.models.substudies;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AccountSubstudyTest {

    @Test
    public void create() {
        AccountSubstudy substudy = AccountSubstudy.create("studyId", "substudyId", "accountId");
        assertEquals("studyId", substudy.getStudyId());
        assertEquals("substudyId", substudy.getSubstudyId());
        assertEquals("accountId", substudy.getAccountId());
    }
    
}
