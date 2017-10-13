package org.sagebionetworks.bridge.json;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.accounts.SignIn;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;

@RunWith(MockitoJUnitRunner.class)
public class SignInDeserializerTest {

    @Mock
    private JsonParser parser;
    
    @Mock
    private DeserializationContext context;
    
    @Mock
    private ObjectCodec codec;
    
    @Test
    public void test() throws Exception {
        JsonNode node = BridgeObjectMapper.get().readTree(TestUtils.createJson("{"+
                "'email':'emailValue',"+
                "'password':'passwordValue',"+
                "'study':'studyValue',"+
                "'token':'tokenValue',"+
                "'reauthToken':'reauthTokenValue'"+
                "}"));
        when(parser.getCodec()).thenReturn(codec);
        when(codec.readTree(any())).thenReturn(node);
        SignInDeserializer deserializer = new SignInDeserializer();
        
        SignIn signIn = deserializer.deserialize(parser, context);
        assertEquals("emailValue", signIn.getEmail());
        assertEquals("passwordValue", signIn.getPassword());
        assertEquals("studyValue", signIn.getStudyId());
        assertEquals("tokenValue", signIn.getToken());
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
        when(parser.getCodec()).thenReturn(codec);
        when(codec.readTree(any())).thenReturn(node);
        SignInDeserializer deserializer = new SignInDeserializer();
        
        SignIn signIn = deserializer.deserialize(parser, context);
        assertEquals("emailValue", signIn.getEmail());
        assertEquals("passwordValue", signIn.getPassword());
        assertEquals("studyValue", signIn.getStudyId());
        assertEquals("tokenValue", signIn.getToken());
        assertEquals("reauthTokenValue", signIn.getReauthToken());
    }
    
}
