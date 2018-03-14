package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

public class IdentifierUpdateTest {
    
    @Test
    public void canSerialize() throws Exception {
        SignIn signIn = new SignIn.Builder().withEmail(TestConstants.EMAIL).withPassword(TestConstants.PASSWORD)
                .build();
        
        // You wouldn't normally send two updates, but for the sake of verifying serialization...
        IdentifierUpdate update = new IdentifierUpdate(signIn, "updated@email.com", TestConstants.PHONE, "updatedExternalId");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(update);
        assertEquals("updated@email.com", node.get("emailUpdate").textValue());
        assertEquals("updatedExternalId", node.get("externalIdUpdate").textValue());
        assertEquals("IdentifierUpdate", node.get("type").textValue());
        
        JsonNode phoneNode = node.get("phoneUpdate");
        assertEquals(TestConstants.PHONE.getNationalFormat(), phoneNode.get("nationalFormat").textValue());
        
        JsonNode signInNode = node.get("signIn");
        assertEquals(TestConstants.EMAIL, signInNode.get("email").textValue());
        assertEquals(TestConstants.PASSWORD, signInNode.get("password").textValue());
        
        IdentifierUpdate deser = BridgeObjectMapper.get().readValue(node.toString(), IdentifierUpdate.class);
        assertEquals("updated@email.com", deser.getEmailUpdate());
        assertEquals("updatedExternalId", deser.getExternalIdUpdate());
        assertEquals(TestConstants.PHONE, deser.getPhoneUpdate());
        assertEquals(TestConstants.EMAIL, deser.getSignIn().getEmail());
        assertEquals(TestConstants.PASSWORD, deser.getSignIn().getPassword());
    }

}
