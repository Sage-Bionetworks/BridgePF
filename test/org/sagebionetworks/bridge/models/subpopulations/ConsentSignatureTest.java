package org.sagebionetworks.bridge.models.subpopulations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;

import nl.jqno.equalsverifier.EqualsVerifier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

public class ConsentSignatureTest {
    private static final DateTime CONSENT_CREATED_ON_TIMESTAMP = DateTime.now(DateTimeZone.UTC).minusDays(1);
    private static final DateTime SIGNED_ON_TIMESTAMP = DateTime.now(DateTimeZone.UTC);
    private static final DateTime WITHDREW_ON_TIMESTAMP = DateTime.now(DateTimeZone.UTC).plusDays(1);
    
    @Before
    public void before() {
        DateTimeUtils.setCurrentMillisFixed(SIGNED_ON_TIMESTAMP.getMillis());
    }
    
    @After
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @Test
    public void allDatesFormattedCorrectly() throws Exception {
        ConsentSignature signature = new ConsentSignature.Builder()
            .withBirthdate("1970-01-01")
            .withName("Dave Test")
            .withWithdrewOn(WITHDREW_ON_TIMESTAMP.getMillis())
            .withConsentCreatedOn(CONSENT_CREATED_ON_TIMESTAMP.getMillis())
            .withSignedOn(SIGNED_ON_TIMESTAMP.getMillis()).build();
        
        String json = ConsentSignature.SIGNATURE_WRITER.writeValueAsString(signature);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertNull(node.get("consentCreatedOn"));
        assertEquals(WITHDREW_ON_TIMESTAMP.toString(),
                node.get("withdrewOn").textValue());
        assertEquals(SIGNED_ON_TIMESTAMP.toString(), 
                node.get("signedOn").textValue());
        assertEquals("ConsentSignature", node.get("type").textValue());
        
        ConsentSignature deser = ConsentSignature.fromJSON(node);
        assertEquals("Dave Test", deser.getName());
        assertEquals("1970-01-01", deser.getBirthdate());
        assertEquals(SIGNED_ON_TIMESTAMP.getMillis(), deser.getSignedOn()); // this is set in the builder
        assertEquals(0L, deser.getConsentCreatedOn());
        assertNull(deser.getWithdrewOn());
    }
    
    @Test
    public void happyCase() {
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withBirthdate("1970-01-01")
                .withConsentCreatedOn(CONSENT_CREATED_ON_TIMESTAMP.getMillis())
                .withSignedOn(SIGNED_ON_TIMESTAMP.getMillis()).build();
        assertEquals("test name", sig.getName());
        assertEquals("1970-01-01", sig.getBirthdate());
        assertEquals(CONSENT_CREATED_ON_TIMESTAMP.getMillis(), sig.getConsentCreatedOn());
        assertNull(sig.getImageData());
        assertNull(sig.getImageMimeType());
    }

    @Test
    public void withImage() {
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withBirthdate("1970-01-01")
                .withImageData(TestConstants.DUMMY_IMAGE_DATA).withImageMimeType("image/fake")
                .withSignedOn(SIGNED_ON_TIMESTAMP.getMillis()).build();
        assertEquals("test name", sig.getName());
        assertEquals("1970-01-01", sig.getBirthdate());
        assertEquals(TestConstants.DUMMY_IMAGE_DATA, sig.getImageData());
        assertEquals("image/fake", sig.getImageMimeType());
    }

    @Test
    public void jsonHappyCase() throws Exception {
        String jsonStr = "{\"name\":\"test name\", \"birthdate\":\"1970-01-01\"}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        assertEquals("test name", sig.getName());
        assertEquals("1970-01-01", sig.getBirthdate());
        assertNull(sig.getImageData());
        assertNull(sig.getImageMimeType());
    }

    @Test
    public void jsonHappyCaseNullImage() throws Exception {
        String jsonStr = "{\n" +
                "   \"name\":\"test name\",\n" +
                "   \"birthdate\":\"1970-01-01\",\n" +
                "   \"imageData\":null,\n" +
                "   \"imageMimeType\":null\n" +
                "}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        assertEquals("test name", sig.getName());
        assertEquals("1970-01-01", sig.getBirthdate());
        assertNull(sig.getImageData());
        assertNull(sig.getImageMimeType());
    }

    @Test
    public void jsonHappyCaseWithImage() throws Exception {
        String jsonStr = "{\n" +
                "   \"name\":\"test name\",\n" +
                "   \"birthdate\":\"1970-01-01\",\n" +
                "   \"imageData\":\"" + TestConstants.DUMMY_IMAGE_DATA + "\",\n" +
                "   \"imageMimeType\":\"image/fake\"\n" +
                "}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        assertEquals("test name", sig.getName());
        assertEquals("1970-01-01", sig.getBirthdate());
        assertEquals(TestConstants.DUMMY_IMAGE_DATA, sig.getImageData());
        assertEquals("image/fake", sig.getImageMimeType());
        assertEquals(SIGNED_ON_TIMESTAMP.getMillis(), sig.getSignedOn());
    }
    
    @Test
    public void existingSignatureJsonDeserializesWithoutSignedOn() throws Exception {
        String json = "{\"name\":\"test name\",\"birthdate\":\"1970-01-01\"}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(json, ConsentSignature.class);
        assertEquals("test name", sig.getName());
        assertEquals("1970-01-01", sig.getBirthdate());
        assertEquals(SIGNED_ON_TIMESTAMP.getMillis(), sig.getSignedOn());
    }
    
    @Test
    public void migrationConstructorUpdatesSignedOnValue() throws Exception {
        String json = "{\"name\":\"test name\",\"birthdate\":\"1970-01-01\"}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(json, ConsentSignature.class);

        ConsentSignature updated = new ConsentSignature.Builder().withConsentSignature(sig)
                .withSignedOn(SIGNED_ON_TIMESTAMP.getMillis()).build();
        assertEquals("test name", updated.getName());
        assertEquals("1970-01-01", updated.getBirthdate());
        assertEquals(SIGNED_ON_TIMESTAMP.getMillis(), updated.getSignedOn());
        
        json = "{\"name\":\"test name\",\"birthdate\":\"1970-01-01\",\"signedOn\":\"" + 
                SIGNED_ON_TIMESTAMP.toString() + "\"}";
        sig = BridgeObjectMapper.get().readValue(json, ConsentSignature.class);
        assertEquals(SIGNED_ON_TIMESTAMP.getMillis(), sig.getSignedOn());
    }
    
    @Test
    public void equalsAndHashCodeAreCorrect() {
        EqualsVerifier.forClass(ConsentSignature.class).allFieldsShouldBeUsed().verify();
    }
}
