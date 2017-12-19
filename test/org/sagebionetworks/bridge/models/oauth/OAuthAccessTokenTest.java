package org.sagebionetworks.bridge.models.oauth;

import static org.junit.Assert.assertEquals;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;

public class OAuthAccessTokenTest {

    private static final String TYPE_NAME = "OAuthAccessToken";
    private static final String PROVIDER_USER_ID = "providerUserId";
    private static final String ACCESS_TOKEN = "accessToken";
    private static final String VENDOR_ID = "vendorId";
    private static final DateTime DATE_TIME = DateTime.now(DateTimeZone.UTC);

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(OAuthAccessToken.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        OAuthAccessToken token = new OAuthAccessToken(VENDOR_ID, ACCESS_TOKEN, DATE_TIME, PROVIDER_USER_ID);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(token);
        assertEquals(VENDOR_ID, node.get(VENDOR_ID).textValue());
        assertEquals(ACCESS_TOKEN, node.get(ACCESS_TOKEN).textValue());
        assertEquals(DATE_TIME.toString(), node.get("expiresOn").textValue());
        assertEquals(PROVIDER_USER_ID, node.get(PROVIDER_USER_ID).textValue());
        assertEquals(TYPE_NAME, node.get("type").textValue());
        assertEquals(5, node.size());
        
        OAuthAccessToken deser = BridgeObjectMapper.get().readValue(node.toString(), OAuthAccessToken.class);
        assertEquals(VENDOR_ID, deser.getVendorId());
        assertEquals(ACCESS_TOKEN, deser.getAccessToken());
        assertEquals(DATE_TIME.toString(), deser.getExpiresOn().toString());
        assertEquals(PROVIDER_USER_ID, deser.getProviderUserId());
        
        assertEquals(token, deser);
    }
}
