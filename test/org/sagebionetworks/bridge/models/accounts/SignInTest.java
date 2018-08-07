package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

public class SignInTest {

    @Test
    public void canDeserializeOldJson() throws Exception {
        String oldJson = "{\"username\":\"aName\",\"password\":\"password\"}";

        SignIn signIn = BridgeObjectMapper.get().readValue(oldJson, SignIn.class);

        assertEquals("aName", signIn.getEmail());
        assertEquals("password", signIn.getPassword());
    }
    
    @Test
    public void canDeserialize() throws Exception {
        String json = TestUtils.createJson("{'study':'foo','email':'aName','password':'password','token':'ABC'}");

        SignIn signIn = BridgeObjectMapper.get().readValue(json, SignIn.class);

        assertEquals("foo", signIn.getStudyId());
        assertEquals("aName", signIn.getEmail());
        assertEquals("password", signIn.getPassword());
        assertEquals("ABC", signIn.getToken());
    }
    
    @Test
    public void canSerialize() throws Exception {
        // We set up tests with this object so verify it creates the correct JSON
        SignIn signIn = new SignIn.Builder().withEmail("email@email.com").withExternalId("external-id")
                .withPassword("password").withPhone(TestConstants.PHONE).withReauthToken("reauthToken")
                .withStudy("study-key").withToken("token").build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(signIn);
        assertEquals("email@email.com", node.get("email").textValue());
        assertEquals("password", node.get("password").textValue());
        assertEquals(TestConstants.PHONE.getNumber(), node.get("phone").get("number").textValue());
        assertEquals(TestConstants.PHONE.getRegionCode(), node.get("phone").get("regionCode").textValue());
        assertEquals("reauthToken", node.get("reauthToken").textValue());
        assertEquals("study-key", node.get("study").textValue());
        assertEquals("external-id", node.get("externalId").textValue());
        assertEquals("token", node.get("token").textValue());
    }
    
    @Test
    public void preferUsernameOverEmailForBackwardsCompatibility() throws Exception {
        String json = "{\"username\":\"aName\",\"email\":\"email@email.com\",\"password\":\"password\"}";

        SignIn signIn = BridgeObjectMapper.get().readValue(json, SignIn.class);

        assertEquals("aName", signIn.getEmail());
        assertEquals("password", signIn.getPassword());
    }
    
    @Test
    public void canSendReauthenticationToken() throws Exception {
        String json = "{\"email\":\"email@email.com\",\"reauthToken\":\"myReauthToken\"}";

        SignIn signIn = BridgeObjectMapper.get().readValue(json, SignIn.class);

        assertEquals("email@email.com", signIn.getEmail());
        assertEquals("myReauthToken", signIn.getReauthToken());
    }
    
    @Test
    public void test() throws Exception {
        JsonNode node = BridgeObjectMapper.get().readTree(TestUtils.createJson("{"+
                "'email':'emailValue',"+
                "'externalId':'external-id',"+
                "'password':'passwordValue',"+
                "'study':'studyValue',"+
                "'token':'tokenValue',"+
                "'phone':{'number':'"+TestConstants.PHONE.getNumber()+"',"+
                    "'regionCode':'"+TestConstants.PHONE.getRegionCode()+"'},"+
                "'reauthToken':'reauthTokenValue'"+
                "}"));
        
        SignIn signIn = BridgeObjectMapper.get().readValue(node.toString(), SignIn.class);
        assertEquals("emailValue", signIn.getEmail());
        assertEquals("external-id", signIn.getExternalId());
        assertEquals("passwordValue", signIn.getPassword());
        assertEquals("studyValue", signIn.getStudyId());
        assertEquals("tokenValue", signIn.getToken());
        assertEquals(TestConstants.PHONE.getNumber(), signIn.getPhone().getNumber());
        assertEquals(TestConstants.PHONE.getRegionCode(), signIn.getPhone().getRegionCode());
        assertEquals("reauthTokenValue", signIn.getReauthToken());
    }
    
    @Test
    public void acceptsUsernameAsEmail() throws Exception {
        JsonNode node = BridgeObjectMapper.get().readTree(TestUtils.createJson("{"+
                "'username':'emailValue',"+
                "'password':'passwordValue',"+
                "'study':'studyValue',"+
                "'token':'tokenValue',"+
                "'reauthToken':'reauthTokenValue'"+
                "}"));
        SignIn signIn = BridgeObjectMapper.get().readValue(node.toString(), SignIn.class);
        assertEquals("emailValue", signIn.getEmail());
        assertEquals("passwordValue", signIn.getPassword());
        assertEquals("studyValue", signIn.getStudyId());
        assertEquals("tokenValue", signIn.getToken());
        assertEquals("reauthTokenValue", signIn.getReauthToken());
    }
    
    @Test
    public void signInAccountIdWithEmail() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER).withEmail("email")
                .withPassword("password").build();
        AccountId accountId = signIn.getAccountId();
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, accountId.getStudyId());
        assertEquals("email", accountId.getEmail());
    }
    
    @Test
    public void signInAccountIdWithPhone() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withPhone(TestConstants.PHONE).withPassword("password").build();
        AccountId accountId = signIn.getAccountId();
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, accountId.getStudyId());
        assertEquals(TestConstants.PHONE.getNumber(), accountId.getPhone().getNumber());
    }
    
    @Test
    public void signInAccountIdWithExternalId() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withExternalId("external-id").withPassword("password").build();
        AccountId accountId = signIn.getAccountId();
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, accountId.getStudyId());
        assertEquals("external-id", accountId.getExternalId());
    }
    
    @Test
    public void signInAccountIncomplete() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withPassword("password").build();
        // SignIn should be validated to hold either email or phone before we 
        // retrieve accountId 
        try {
            signIn.getAccountId();
            fail("Should have thrown an exception");
        } catch(IllegalArgumentException e) {
            assertEquals("SignIn not constructed with enough information to retrieve an account", e.getMessage());
        }
    }
    
    @Test
    public void fullCopy() {
        SignIn origin = new SignIn.Builder()
                .withUsername(TestConstants.EMAIL)
                .withPhone(TestConstants.PHONE)
                .withExternalId("externalId")
                .withPassword("password")
                .withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withToken("token")
                .withReauthToken("reauthToken").build();
        
        SignIn copy = new SignIn.Builder().withSignIn(origin).build();
        assertEquals(TestConstants.EMAIL, copy.getEmail());
        assertEquals(TestConstants.PHONE, copy.getPhone());
        assertEquals("externalId", copy.getExternalId());
        assertEquals("password", copy.getPassword());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, copy.getStudyId());
        assertEquals("token", copy.getToken());
        assertEquals("reauthToken", copy.getReauthToken());
        
        // Also test the straight email-to-email copy as well as the username copy
        assertEquals("email", new SignIn.Builder().withSignIn(
                new SignIn.Builder().withEmail("email").build()
            ).build().getEmail());
    }
}
