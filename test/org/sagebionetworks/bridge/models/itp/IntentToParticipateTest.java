package org.sagebionetworks.bridge.models.itp;

import static org.junit.Assert.assertEquals;

import org.joda.time.DateTime;
import org.junit.Test;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;

import com.fasterxml.jackson.databind.JsonNode;

public class IntentToParticipateTest {
    
    private static final long TIMESTAMP = DateTime.now().getMillis();
    
    @Test
    public void canSerialize() throws Exception {
        ConsentSignature consentSignature = new ConsentSignature.Builder().withName("Consent Name")
                .withBirthdate("1980-10-10").withImageData("image-data").withImageMimeType("image/png")
                .withSignedOn(TIMESTAMP).withConsentCreatedOn(TIMESTAMP).build();
        
        IntentToParticipate itp = new IntentToParticipate.Builder().withStudy("study").withEmail("email@email.com")
                .withPhone("phone").withSubpopGuid("subpopGuid").withScope(SharingScope.ALL_QUALIFIED_RESEARCHERS)
                .withConsentSignature(consentSignature).build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(itp);
        assertEquals("study", node.get("study").textValue());
        assertEquals("email@email.com", node.get("email").textValue());
        assertEquals("phone", node.get("phone").textValue());
        assertEquals("subpopGuid", node.get("subpopGuid").textValue());
        assertEquals("all_qualified_researchers", node.get("scope").textValue());
        assertEquals("IntentToParticipate", node.get("type").textValue());
        assertEquals(7, node.size());
        
        JsonNode consentNode = node.get("consentSignature");
        assertEquals("Consent Name", consentNode.get("name").textValue());
        assertEquals("1980-10-10", consentNode.get("birthdate").textValue());
        assertEquals("image-data", consentNode.get("imageData").textValue());
        assertEquals("image/png", consentNode.get("imageMimeType").textValue());
        assertEquals(TIMESTAMP, consentNode.get("consentCreatedOn").longValue());
        assertEquals(TIMESTAMP, consentNode.get("signedOn").longValue());
        assertEquals("ConsentSignature", consentNode.get("type").textValue());
        assertEquals(7, consentNode.size());
        
        IntentToParticipate deser = BridgeObjectMapper.get().readValue(node.toString(), IntentToParticipate.class);
        assertEquals("study", deser.getStudy());
        assertEquals("email@email.com", deser.getEmail());
        assertEquals("phone", deser.getPhone());
        assertEquals("subpopGuid", deser.getSubpopGuid());
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, deser.getScope());

        ConsentSignature consentDeser = deser.getConsentSignature();
        assertEquals("Consent Name", consentDeser.getName());
        assertEquals("1980-10-10", consentDeser.getBirthdate());
        assertEquals("image-data", consentDeser.getImageData());
        assertEquals("image/png", consentDeser.getImageMimeType());
        assertEquals(TIMESTAMP, consentDeser.getConsentCreatedOn());
        assertEquals(TIMESTAMP, consentDeser.getSignedOn());
    }
}
