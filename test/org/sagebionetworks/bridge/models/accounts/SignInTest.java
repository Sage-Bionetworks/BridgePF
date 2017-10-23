package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

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
                "'phone':'1234567890',"+
                "'reauthToken':'reauthTokenValue'"+
                "}"));
        
        SignIn signIn = BridgeObjectMapper.get().readValue(node.toString(), SignIn.class);
        assertEquals("emailValue", signIn.getEmail());
        assertEquals("passwordValue", signIn.getPassword());
        assertEquals("studyValue", signIn.getStudyId());
        assertEquals("tokenValue", signIn.getToken());
        assertEquals("1234567890", signIn.getPhone());
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
}
