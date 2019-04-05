package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;


import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.accounts.AccountId;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import nl.jqno.equalsverifier.EqualsVerifier;

public class CriteriaContextTest {
    
    private static final ClientInfo CLIENT_INFO = ClientInfo.parseUserAgentString("app/20");
    private static final String USER_ID = "user-id";
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(CriteriaContext.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void defaultsClientInfo() {
        CriteriaContext context = new CriteriaContext.Builder()
                .withUserId(USER_ID)
                .withStudyIdentifier(TestConstants.TEST_STUDY).build();
        assertEquals(ClientInfo.UNKNOWN_CLIENT, context.getClientInfo());
        assertEquals(ImmutableList.of(), context.getLanguages());
        assertEquals(ImmutableSet.of(), context.getUserDataGroups());
        assertEquals(ImmutableSet.of(), context.getUserSubstudyIds());
    }
    
    @Test(expected = NullPointerException.class)
    public void requiresStudyIdentifier() {
        new CriteriaContext.Builder().withUserId(USER_ID).build();
    }
    
    @Test
    public void builderWorks() {
        CriteriaContext context = new CriteriaContext.Builder()
                .withStudyIdentifier(TestConstants.TEST_STUDY)
                .withUserId(USER_ID)
                .withClientInfo(CLIENT_INFO)
                .withUserDataGroups(TestConstants.USER_DATA_GROUPS)
                .withUserSubstudyIds(TestConstants.USER_SUBSTUDY_IDS).build();
        
        // There are defaults
        assertEquals(CLIENT_INFO, context.getClientInfo());
        assertEquals(TestConstants.USER_DATA_GROUPS, context.getUserDataGroups());
        assertEquals(TestConstants.USER_SUBSTUDY_IDS, context.getUserSubstudyIds());
        
        CriteriaContext copy = new CriteriaContext.Builder().withContext(context).build();
        assertEquals(CLIENT_INFO, copy.getClientInfo());
        assertEquals(TestConstants.TEST_STUDY, copy.getStudyIdentifier());
        assertEquals(USER_ID, copy.getUserId());
        assertEquals(TestConstants.USER_DATA_GROUPS, copy.getUserDataGroups());
        assertEquals(TestConstants.USER_SUBSTUDY_IDS, copy.getUserSubstudyIds());
    }
    
    @Test
    public void contextHasAccountId() {
        CriteriaContext context = new CriteriaContext.Builder()
                .withStudyIdentifier(TestConstants.TEST_STUDY)
                .withUserId(USER_ID).build();
        
        AccountId accountId = context.getAccountId();
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, accountId.getStudyId());
        assertEquals(USER_ID, accountId.getId());
    }
}
