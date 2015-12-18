package org.sagebionetworks.bridge.stormpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.crypto.Encryptor;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.collect.Lists;
import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.directory.CustomData;

public class StormpathAccountTest {
    
    private static final TypeReference<List<ConsentSignature>> CONSENT_SIGNATURES_TYPE = new TypeReference<List<ConsentSignature>>() {};
    
    private static final BridgeObjectMapper MAPPER = BridgeObjectMapper.get();
    private static final long UNIX_TIMESTAMP = DateTime.now().getMillis();
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("foo");
    private static final SubpopulationGuid SUBPOP_GUID_2 = SubpopulationGuid.create("foo2");
    private static final SubpopulationGuid SUBPOP_GUID_3 = SubpopulationGuid.create("foo3");
    private static final StudyIdentifier STUDY_ID = new StudyIdentifierImpl("foo");
    private static final List<? extends SubpopulationGuid> SUBPOP_GUIDS = Lists.newArrayList(SUBPOP_GUID, SUBPOP_GUID_2,
            SUBPOP_GUID_3);

    @SuppressWarnings("serial")
    private class StubCustomData extends HashMap<String,Object> implements CustomData {
        @Override public String getHref() { return null; }
        @Override public void save() {}
        @Override public void delete() {}
        @Override public Date getCreatedAt() { return null; }
        @Override public Date getModifiedAt() { return null; }
    }

    private StubCustomData data;
    
    private Account account;
    
    private StormpathAccount acct;
    
    private ConsentSignature sig;
    
    private  SortedMap<Integer, BridgeEncryptor> encryptors;
    
    @Before
    public void setUp() throws Exception {
        account = mock(Account.class);
        data = new StubCustomData(); 
        when(account.getCustomData()).thenReturn(data);

        BridgeEncryptor encryptor1 = mock(BridgeEncryptor.class);
        when(encryptor1.getVersion()).thenReturn(1);
        encryptDecryptValues(encryptor1, "1");

        BridgeEncryptor encryptor2 = mock(BridgeEncryptor.class);
        when(encryptor2.getVersion()).thenReturn(2);
        encryptDecryptValues(encryptor2, "2");
          
        encryptors = new TreeMap<>();
        encryptors.put(1, encryptor1);
        encryptors.put(2, encryptor2);
        
        // In some tests this is stored under older keys, such as the key that was used before supporting
        // multipl encryptor versions.
        sig = new ConsentSignature.Builder().withName("Test").withBirthdate("1970-01-01").withImageData("test")
                .withImageMimeType("image/png").withSignedOn(UNIX_TIMESTAMP).build();
        
        acct = new StormpathAccount(STUDY_ID, SUBPOP_GUIDS, account, encryptors);
    }
    
    private void encryptDecryptValues(final Encryptor encryptor, final String version) {
        when(encryptor.encrypt(any())).thenAnswer(invocation -> {
            return "encrypted-" + version + "-" + invocation.getArgumentAt(0, String.class);
        });
        when(encryptor.decrypt(any())).thenAnswer(invocation -> {
            String encValue = invocation.getArgumentAt(0, String.class);
            return (encValue == null) ? encValue : encValue.replace("encrypted-"+version+"-", "");
        });
    }
    
    @Test
    public void consentSignaturesEncrypted() throws Exception {
        List<ConsentSignature> signatures = acct.getConsentSignatureHistory(SUBPOP_GUID);
        signatures.add(new ConsentSignature.Builder().withName("Another Name").withBirthdate("1983-05-10").build());
        
        String json = BridgeObjectMapper.get().writeValueAsString(acct.getConsentSignatureHistory(SUBPOP_GUID));
        acct.getAccount(); // necessary to trigger update of customData
        
        assertEquals("encrypted-2-"+json, data.get("foo_consent_signatures"));
        assertEquals(signatures, acct.getConsentSignatureHistory(SUBPOP_GUID));
    }
    
    @Test
    public void basicFieldWorks() {
        when(account.getEmail()).thenReturn("test@test.com");
        
        acct.setEmail("test@test.com");
        assertEquals("test@test.com", acct.getEmail());
        
        verify(account).setEmail("test@test.com");
    }
    
    @Test
    public void basicFieldValueCanBeCleared() {
        when(account.getEmail()).thenReturn(null);
        
        acct.setEmail(null);
        assertNull(acct.getEmail());
        
        verify(account).setEmail(null);
    }
    
    @Test
    public void newSensitiveValueIsEncryptedWithLastEncryptor() {
        acct.setAttribute("phone", "111-222-3333");
        
        assertEquals("encrypted-2-111-222-3333", data.get("phone"));
        assertEquals("111-222-3333", acct.getAttribute("phone"));
    }
    
    @Test
    public void sensitiveValueWhenClearedRemovesVersionKey() {
        acct.setAttribute("phone", "111-222-3333");
        assertEquals(2, data.get("phone_version"));
        
        acct.setAttribute("phone", null);
        assertNull(data.get("phone"));
        assertNull(data.get("phone_version"));
    }
    
    @Test
    public void noValueSupported() {
        assertNull(acct.getAttribute("phone"));
    }
    
    @Test
    public void oldValuesDecryptedWithOldDecryptorAndEncryptedWithNewDecryptor() {
        data.put("phone", "encrypted-1-111-222-3333");
        data.put("phone_version", 1);

        assertEquals("111-222-3333", acct.getAttribute("phone"));
        
        acct.setAttribute("phone", acct.getAttribute("phone"));
        
        assertEquals("encrypted-2-111-222-3333", data.get("phone"));
        assertEquals("111-222-3333", acct.getAttribute("phone"));
    }
    
    @Test
    public void consentSignatureRAndEncrypted() {
        acct.getConsentSignatureHistory(SUBPOP_GUID).add(sig);
        
        ConsentSignature restoredSig = acct.getActiveConsentSignature(SUBPOP_GUID);
        assertEquals("Test", restoredSig.getName());
        assertEquals("1970-01-01", restoredSig.getBirthdate());
        assertEquals("test", restoredSig.getImageData());
        assertEquals("image/png", restoredSig.getImageMimeType());
        assertEquals(UNIX_TIMESTAMP, restoredSig.getSignedOn());
    }
    
    @Test
    public void canClearKeyValue() {
        acct.setAttribute("phone", "111-222-3333");
        acct.setAttribute("phone", null);
        
        assertNull(acct.getAttribute("phone"));
    }
    
    @Test
    public void healthIdRetrievedWithNewVersion() {
        data.put("foo_code", "encrypted-1-aHealthId");
        data.put("foo_code_version", 1);
        
        String healthId = acct.getHealthId();
        assertEquals("aHealthId", healthId);
    }
    
    @Test
    public void healthIdRetrievedWithOldVersion() {
        data.put("foo_code", "encrypted-1-HealthId");
        data.put("fooversion", 1);
        
        String healthId = acct.getHealthId();
        assertEquals("HealthId", healthId);
    }
    
    @Test
    public void consentSignatureRetrievedWithNoVersion() throws Exception {
        // This is a key that predates the encryptors. SHould still be found when we 
        // retrieve the value through the new consent signature list.
        data.put("foo_consent_signature", MAPPER.writeValueAsString(sig));
        
        acct = new StormpathAccount(STUDY_ID, SUBPOP_GUIDS, account, encryptors);
        
        ConsentSignature restoredSig = acct.getActiveConsentSignature(SUBPOP_GUID);
        assertEquals("Test", restoredSig.getName());
        assertEquals("1970-01-01", restoredSig.getBirthdate());
        assertEquals("test", restoredSig.getImageData());
        assertEquals("image/png", restoredSig.getImageMimeType());
    }
    
    @Test
    public void consentSignatureRetrievedFromEncryptedSingleObjectSlot() throws Exception {
        data.put("foo_consent_signature", MAPPER.writeValueAsString(sig));
        data.put("foo_consent_signature_version", 2);
        
        acct = new StormpathAccount(STUDY_ID, SUBPOP_GUIDS, account, encryptors);
        
        ConsentSignature restoredSig = acct.getActiveConsentSignature(SUBPOP_GUID);
        assertEquals("Test", restoredSig.getName());
        assertEquals("1970-01-01", restoredSig.getBirthdate());
        assertEquals("test", restoredSig.getImageData());
        assertEquals("image/png", restoredSig.getImageMimeType());
        
        // Also should be in "history"
        List<ConsentSignature> history = acct.getConsentSignatureHistory(SUBPOP_GUID);
        assertEquals(1, history.size());
        assertEquals(sig, history.get(0));
        assertNull(history.get(0).getWithdrewOn());
    }
    
    @Test
    public void consentSignatureRetrievedWithOnlySignatureList() throws Exception {
        List<ConsentSignature> history = Lists.newArrayList();
        // Adding a historical signature. Order matters, the active one must be last.
        history.add(new ConsentSignature.Builder().withName("Stephen Maturin").withBirthdate("1790-04-12")
                .withWithdrewOn(DateTime.now().getMillis()).build());
        history.add(sig);
        data.put("foo_consent_signatures", MAPPER.writeValueAsString(history));
        data.put("foo_consent_signatures_version", 2);
        
        acct = new StormpathAccount(STUDY_ID, SUBPOP_GUIDS, account, encryptors);
        
        ConsentSignature restoredSig = acct.getActiveConsentSignature(SUBPOP_GUID);
        assertEquals("Test", restoredSig.getName());
        assertEquals("1970-01-01", restoredSig.getBirthdate());
        assertEquals("test", restoredSig.getImageData());
        assertEquals("image/png", restoredSig.getImageMimeType());
    }
    
    @Test
    public void activeConsentIsNullWhenAllConsentsWithdrawn() throws Exception {
        sig = new ConsentSignature.Builder().withConsentSignature(sig).withWithdrewOn(DateTime.now().getMillis()).build();
        
        List<ConsentSignature> history = Lists.newArrayList();
        history.add(sig);
        history.add(new ConsentSignature.Builder().withName("Stephen Maturin").withBirthdate("1790-04-12")
                .withWithdrewOn(DateTime.now().getMillis()).build());
        
        data.put("foo_consent_signatures", MAPPER.writeValueAsString(history));
        data.put("foo_consent_signatures_version", 2);
        
        acct = new StormpathAccount(STUDY_ID, SUBPOP_GUIDS, account, encryptors);
        
        ConsentSignature restoredSig = acct.getActiveConsentSignature(SUBPOP_GUID);
        assertNull(restoredSig);
    }
    
    @Test
    public void failsIfNoEncryptorForVersion() {
        try {
            data.put("foo_code", "111");
            data.put("fooversion", 3);
            
            acct.getHealthId();
        } catch(BridgeServiceException e) {
            assertEquals("No encryptor can be found for version 3", e.getMessage());
        }
    }
    
    @Test
    public void phoneRetrievedWithNoVersion() {
        data.put("phone", "encrypted-2-555-555-5555");
        
        // This must use version 2, there's no version listed.
        String phone = acct.getAttribute("phone");
        assertEquals("555-555-5555", phone);
    }
    
    @Test
    public void phoneRetrievedWithCorrect() {
        data.put("phone", "encrypted-2-555-555-5555");
        data.put("phone_version", 2);
        
        // This must use version 2, there's no version listed.
        String phone = acct.getAttribute("phone");
        assertEquals("555-555-5555", phone);
    }
    
    @Test(expected = BridgeServiceException.class)
    public void phoneNotRetrievedWithIncorrectVersion() {
        data.put("phone", "encryptedphonenumber");
        data.put("phone_version", 3);
        
        acct.getAttribute("phone");
    }
    
    @Test
    public void retrievingNullEncryptedFieldReturnsNull() {
        String phone = acct.getAttribute("phone");
        assertNull(phone);
    }
    
    @Test
    public void canSetAndGetRoles() {
        acct.getRoles().add(DEVELOPER);
        
        assertEquals(1, acct.getRoles().size());
        assertEquals(DEVELOPER, acct.getRoles().iterator().next());
    }
    
    // see the StormpathAccountDaoTest.canSetAndRetrieveConsentsForMultipleSubpopulations test where we 
    // test encryption into and out of Stormpath.
    @Test
    public void multipleConsentsAreMaintainedSeparately() throws Exception {
        ConsentSignature sig1 = new ConsentSignature.Builder()
                .withName("Name 1")
                .withBirthdate("2000-10-10")
                .withSignedOn(DateTime.now().getMillis())
                .build();
        
        ConsentSignature sig2 = new ConsentSignature.Builder()
                .withName("Name 2")
                .withBirthdate("2000-02-02")
                .withSignedOn(DateTime.now().getMillis())
                .build();
        acct.getConsentSignatureHistory(SUBPOP_GUID).add(sig1);
        acct.getConsentSignatureHistory(SUBPOP_GUID_2).add(sig2);
        
        ConsentSignature sig1Retrieved = acct.getActiveConsentSignature(SUBPOP_GUID);
        assertEquals(sig1, sig1Retrieved);
        
        ConsentSignature sig2Retrieved = acct.getActiveConsentSignature(SUBPOP_GUID_2);
        assertEquals(sig2, sig2Retrieved);
        
        Map<SubpopulationGuid,List<ConsentSignature>> signatures = acct.getAllConsentSignatureHistories();
        sig1Retrieved = signatures.get(SUBPOP_GUID).get(0);
        assertEquals(sig1, sig1Retrieved);
        
        sig2Retrieved = signatures.get(SUBPOP_GUID_2).get(0);
        assertEquals(sig2, sig2Retrieved);
        
        // Retrieve the Stormpath implementation to update customData. Verify that the data is passed as is
        // to customData stub.
        acct.getAccount();
        
        // These are being stored in customData correctly... it's still a stub implementation without 
        // encryption though... see StormpathAccountDaoTest.canSetAndRetrieveConsentsForMultipleSubpopulations()
        // where this is really tested against Stormpath, including encryption.
        verifyOneConsentStream(SUBPOP_GUID, sig1);
        verifyOneConsentStream(SUBPOP_GUID_2, sig2);
    }

    private void verifyOneConsentStream(SubpopulationGuid guid, ConsentSignature sig1)
            throws IOException, JsonParseException, JsonMappingException {
        Integer version = (Integer)data.get(guid.getGuid()+"_consent_signatures_version");
        assertEquals(new Integer(2), version);
        
        // The mock implementation of customData prefixes stuff... we'll unprefix it and parse it into JSON 
        String contentJson = (String)data.get(guid.getGuid()+"_consent_signatures");
        assertTrue(contentJson.startsWith("encrypted-2-"));
        
        String json = contentJson.substring("encrypted-2-".length(), contentJson.length());
        
        List<ConsentSignature> returnedSignatures = BridgeObjectMapper.get().readValue(json, CONSENT_SIGNATURES_TYPE);
        assertEquals(sig1, returnedSignatures.get(0));
    }
}
