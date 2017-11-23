package org.sagebionetworks.bridge.models.oauth;

import static org.junit.Assert.assertEquals;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;

public class OAuthAccessTokenTest {

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(OAuthAccessToken.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        DateTime dateTime = DateTime.now(DateTimeZone.UTC);
        
        OAuthAccessToken token = new OAuthAccessToken("vendorId", "accessToken", dateTime);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(token);
        assertEquals("vendorId", node.get("vendorId").textValue());
        assertEquals("accessToken", node.get("accessToken").textValue());
        assertEquals(dateTime.toString(), node.get("expiresOn").textValue());
        assertEquals("OAuthAccessToken", node.get("type").textValue());
        assertEquals(4, node.size());
        
        OAuthAccessToken deser = BridgeObjectMapper.get().readValue(node.toString(), OAuthAccessToken.class);
        assertEquals("vendorId", deser.getVendorId());
        assertEquals("accessToken", deser.getAccessToken());
        assertEquals(dateTime.toString(), deser.getExpiresOn().toString());
        
        assertEquals(token, deser);
    }
}
