package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;


import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;

import com.google.common.collect.ImmutableSet;

import nl.jqno.equalsverifier.EqualsVerifier;

public class CriteriaContextTest {
    
    private static final ClientInfo CLIENT_INFO = ClientInfo.parseUserAgentString("app/20");
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(CriteriaContext.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void defaultsClientInfo() {
        CriteriaContext context = new CriteriaContext.Builder()
                .withStudyIdentifier(TestConstants.TEST_STUDY).build();
        assertEquals(ClientInfo.UNKNOWN_CLIENT, context.getClientInfo());
        assertEquals(ImmutableSet.of(), context.getLanguages());
    }
    
    @Test(expected = NullPointerException.class)
    public void requiresStudyIdentifier() {
        new CriteriaContext.Builder().build();
    }
    
    @Test
    public void builderWorks() {
        CriteriaContext context = new CriteriaContext.Builder()
                .withStudyIdentifier(TestConstants.TEST_STUDY)
                .withClientInfo(CLIENT_INFO)
                .withUserDataGroups(TestConstants.USER_DATA_GROUPS).build();
        
        // There are defaults
        assertEquals(CLIENT_INFO, context.getClientInfo());
        assertEquals(TestConstants.USER_DATA_GROUPS, context.getUserDataGroups());
        
        CriteriaContext copy = new CriteriaContext.Builder().withContext(context).build();
        assertEquals(CLIENT_INFO, copy.getClientInfo());
        assertEquals(TestConstants.USER_DATA_GROUPS, copy.getUserDataGroups());
    }
}
