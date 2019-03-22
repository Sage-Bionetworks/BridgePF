package org.sagebionetworks.bridge.models.itp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PHONE;

import org.joda.time.DateTime;
import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;

import com.fasterxml.jackson.databind.JsonNode;

public class IntentToParticipateTest {
    
    private static final long TIMESTAMP = DateTime.now().getMillis();
    
    @Test
    public void canSerialize() throws Exception {
        ConsentSignature consentSignature = new ConsentSignature.Builder().withName("Consent Name")
                .withBirthdate("1980-10-10").withImageData("image-data").withImageMimeType("image/png")
                .withSignedOn(TIMESTAMP).withConsentCreatedOn(TIMESTAMP).build();
        
        IntentToParticipate itp = new IntentToParticipate.Builder().withStudyId("studyId").withPhone(PHONE)
                .withEmail(EMAIL).withSubpopGuid("subpopGuid").withScope(SharingScope.ALL_QUALIFIED_RESEARCHERS)
                .withOsName("iOS").withConsentSignature(consentSignature).build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(itp);
        assertEquals("studyId", node.get("studyId").textValue());
        assertEquals(EMAIL, node.get("email").textValue());
        assertEquals("subpopGuid", node.get("subpopGuid").textValue());
        assertEquals("all_qualified_researchers", node.get("scope").textValue());
        assertEquals("iPhone OS", node.get("osName").textValue());
        assertEquals("IntentToParticipate", node.get("type").textValue());
        assertEquals(8, node.size());
        
        JsonNode phoneNode = node.get("phone");
        assertEquals(PHONE.getNumber(), phoneNode.get("number").textValue());
        assertEquals("US", phoneNode.get("regionCode").textValue());
        assertEquals(PHONE.getNationalFormat(), phoneNode.get("nationalFormat").textValue());
        assertEquals("Phone", phoneNode.get("type").textValue());
        assertEquals(4, phoneNode.size());
        
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
        assertEquals("studyId", deser.getStudyId());
        assertEquals(PHONE.getNationalFormat(), deser.getPhone().getNationalFormat());
        assertEquals("subpopGuid", deser.getSubpopGuid());
        assertEquals("iPhone OS", deser.getOsName());
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, deser.getScope());

        ConsentSignature consentDeser = deser.getConsentSignature();
        assertEquals("Consent Name", consentDeser.getName());
        assertEquals("1980-10-10", consentDeser.getBirthdate());
        assertEquals("image-data", consentDeser.getImageData());
        assertEquals("image/png", consentDeser.getImageMimeType());
        assertEquals(TIMESTAMP, consentDeser.getConsentCreatedOn());
        assertEquals(TIMESTAMP, consentDeser.getSignedOn());
        
        Phone deserPhone = deser.getPhone();
        assertEquals(PHONE.getNumber(), deserPhone.getNumber());
        assertEquals("US", deserPhone.getRegionCode());
        assertEquals(PHONE.getNationalFormat(), deserPhone.getNationalFormat());
    }
    
    @Test
    public void canCopy() throws Exception {
        ConsentSignature consentSignature = new ConsentSignature.Builder().withName("Consent Name")
                .withBirthdate("1980-10-10").withImageData("image-data").withImageMimeType("image/png")
                .withSignedOn(TIMESTAMP).withConsentCreatedOn(TIMESTAMP).build();
        
        IntentToParticipate itp = new IntentToParticipate.Builder().withStudyId("studyId").withPhone(PHONE)
                .withEmail(EMAIL).withSubpopGuid("subpopGuid").withScope(SharingScope.ALL_QUALIFIED_RESEARCHERS)
                .withOsName("iOS").withConsentSignature(consentSignature).build();

        IntentToParticipate copy = new IntentToParticipate.Builder().copyOf(itp).build();
        assertEquals(itp.getStudyId(), copy.getStudyId());
        assertEquals(itp.getPhone().getNumber(), copy.getPhone().getNumber());
        assertEquals(itp.getEmail(), copy.getEmail());
        assertEquals(itp.getSubpopGuid(), copy.getSubpopGuid());
        assertEquals(itp.getScope(), copy.getScope());
        assertEquals(itp.getOsName(), copy.getOsName());
        assertEquals(itp.getConsentSignature(), copy.getConsentSignature());
    }
    
    @Test
    public void osSynonyms() throws Exception {
        ConsentSignature consentSignature = new ConsentSignature.Builder().withName("Consent Name")
                .withBirthdate("1980-10-10").withImageData("image-data").withImageMimeType("image/png")
                .withSignedOn(TIMESTAMP).withConsentCreatedOn(TIMESTAMP).build();
        
        IntentToParticipate itp = new IntentToParticipate.Builder().withStudyId("studyId").withPhone(PHONE)
                .withSubpopGuid("subpopGuid").withScope(SharingScope.ALL_QUALIFIED_RESEARCHERS).withOsName("iOS")
                .withConsentSignature(consentSignature).build();
        
        assertNotEquals(OperatingSystem.IOS, "iOS"); // iOS is a synonym...
        assertEquals(OperatingSystem.IOS, itp.getOsName()); // ... it is translated to the standard constant.
    }
}
