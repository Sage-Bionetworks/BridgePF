package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;

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
                "'password':'passwordValue',"+
                "'study':'studyValue',"+
                "'token':'tokenValue',"+
                "'phone':{'number':'4082588569','regionCode':'US'},"+
                "'reauthToken':'reauthTokenValue'"+
                "}"));
        
        SignIn signIn = BridgeObjectMapper.get().readValue(node.toString(), SignIn.class);
        assertEquals("emailValue", signIn.getEmail());
        assertEquals("passwordValue", signIn.getPassword());
        assertEquals("studyValue", signIn.getStudyId());
        assertEquals("tokenValue", signIn.getToken());
        assertEquals("+14082588569", signIn.getPhone().getNumber());
        assertEquals("US", signIn.getPhone().getRegionCode());
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
}
