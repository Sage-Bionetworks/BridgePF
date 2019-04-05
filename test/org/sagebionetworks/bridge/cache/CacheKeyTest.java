package org.sagebionetworks.bridge.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import nl.jqno.equalsverifier.EqualsVerifier;

public class CacheKeyTest {
    
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("guid");
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(CacheKey.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test(expected = NullPointerException.class)
    public void nullsRejected() {
        CacheKey.appConfigList(null);
    }
    
    @Test
    public void reauthTokenLookupKey() {
        assertEquals("ABC:api:ReauthToken", CacheKey.reauthTokenLookupKey("ABC", TestConstants.TEST_STUDY).toString());
    }
    
    @Test
    public void shortenUrl() {
        assertEquals("ABC:ShortenedUrl", CacheKey.shortenUrl("ABC").toString());
    }
    
    @Test
    public void appConfigList() {
        assertEquals("api:AppConfigList", CacheKey.appConfigList(TestConstants.TEST_STUDY).toString());
    }
    
    @Test
    public void channelThrottling() {
        assertEquals("userId:email:channel-throttling", CacheKey.channelThrottling("email", "userId").toString());
    }
    
    @Test
    public void emailSignInRequest() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail("email@email.com").build();
        assertEquals("email@email.com:api:signInRequest", CacheKey.emailSignInRequest(signIn).toString());
    }
    
    @Test
    public void emailVerification() {
        assertEquals("email@email.com:emailVerificationStatus", CacheKey.emailVerification("email@email.com").toString());
    }
    
    @Test
    public void itpWithPhone() {
        assertEquals("guid:"+TestConstants.PHONE.getNumber()+":api:itp",
                CacheKey.itp(SUBPOP_GUID, TestConstants.TEST_STUDY, TestConstants.PHONE).toString());
    }
    
    @Test
    public void itpWithEmail() {
        assertEquals("guid:email@email.com:api:itp",
                CacheKey.itp(SUBPOP_GUID, TestConstants.TEST_STUDY, "email@email.com").toString());
    }
    
    @Test
    public void lock() {
        assertEquals("value:java.lang.String:lock", CacheKey.lock("value", String.class).toString());
    }
    
    @Test
    public void passwordResetForEmail() {
        assertEquals("sptoken:api", CacheKey.passwordResetForEmail("sptoken", "api").toString());
    }
    
    @Test
    public void passwordResetForPhone() {
        assertEquals("sptoken:phone:" + TestConstants.PHONE.getNumber(),
                CacheKey.passwordResetForPhone("sptoken", TestConstants.PHONE.getNumber()).toString());
    }
    
    @Test
    public void phoneSignInRequest() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withPhone(TestConstants.PHONE).build();
        
        assertEquals(TestConstants.PHONE.getNumber() + ":api:phoneSignInRequest",
                CacheKey.phoneSignInRequest(signIn).toString());
    }
    
    @Test
    public void requestInfo() {
        assertEquals("userId:request-info", CacheKey.requestInfo("userId").toString());
    }
    
    @Test
    public void study() {
        assertEquals("api:study", CacheKey.study("api").toString());
    }    
    
    @Test
    public void subpop() {
        assertEquals("guid:api:Subpopulation", CacheKey.subpop(SUBPOP_GUID, TestConstants.TEST_STUDY).toString());
    }
    
    @Test
    public void subpopList() {
        assertEquals("api:SubpopulationList", CacheKey.subpopList(TestConstants.TEST_STUDY).toString());
    }
    
    @Test
    public void verificationToken() {
        assertEquals("token", CacheKey.verificationToken("token").toString());
    }
    
    @Test
    public void viewKey() {
        assertEquals("a:b:StringBuilder:view", CacheKey.viewKey(StringBuilder.class, "a", "b").toString());
    }
    
    @Test
    public void userIdToSession() {
        assertEquals("userId:session2:user", CacheKey.userIdToSession("userId").toString());
    }
    
    @Test
    public void tokenToUserId() { 
        assertEquals("aSessionToken:session2", CacheKey.tokenToUserId("aSessionToken").toString());
    }
    
    @Test
    public void isPublic() {
        CacheKey privateKey = CacheKey.reauthTokenLookupKey("a", TestConstants.TEST_STUDY);
        assertFalse(CacheKey.isPublic(privateKey.toString()));
        
        CacheKey publicKey = CacheKey.study("studyId");
        assertTrue(CacheKey.isPublic(publicKey.toString()));
    }
}
