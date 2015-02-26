package org.sagebionetworks.bridge.stormpath;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.HashMap;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.crypto.Encryptor;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.directory.CustomData;

public class StormpathAccountTest {
    
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
    
    private String legacySignature;
    
    @Before
    public void setUp() throws Exception {
        StudyIdentifier studyId = new StudyIdentifierImpl("foo");
        
        account = mock(Account.class);
        data = new StubCustomData(); 
        when(account.getCustomData()).thenReturn(data);

        String json = "{\"name\":\"Test\",\"birthdate\":\"1970-01-01\",\"imageData\":\"test\",\"imageMimeType\":\"image/png\"}";

        Encryptor encryptor1 = mock(Encryptor.class);
        when(encryptor1.getVersion()).thenReturn(1);
        encryptDecryptValues(encryptor1, "111-222-3333", "111-222-3333-encryptor1encrypted");
        encryptDecryptValues(encryptor1, "aHealthId", "aHealthId-encryptor1encrypted");
        encryptDecryptValues(encryptor1, json, "json-encryptor1encrypted");
        
        Encryptor encryptor2 = mock(Encryptor.class);
        when(encryptor2.getVersion()).thenReturn(2);
        encryptDecryptValues(encryptor2, "111-222-3333", "111-222-3333-encryptor2encrypted");
        encryptDecryptValues(encryptor2, json, "json-encryptor2encrypted");
        encryptDecryptValues(encryptor2, "555-555-5555", "555-555-5555-encryptor2encrypted");
        
        // This must be a passthrough because we're not going to set the signature through
        // StormpathAccount, we're going to put a legacy state in the map that's stubbing out
        // The CustomData element, and then verify that we can retrieve and deserialize the consent
        // even without a version attribute.
        ConsentSignature sig = ConsentSignature.create("Test", "1970-01-01", "test", "image/png");
        legacySignature = new ObjectMapper().writeValueAsString(sig);
        encryptDecryptValues(encryptor2, legacySignature, legacySignature);
        
        SortedMap<Integer,Encryptor> encryptors = new TreeMap<>();
        encryptors.put(1, encryptor1);
        encryptors.put(2, encryptor2);
        
        acct = new StormpathAccount(studyId, account, encryptors);
    }
    
    private void encryptDecryptValues(Encryptor encryptor, String value, String encryptedValue) {
        when(encryptor.encrypt(value)).thenReturn(encryptedValue);
        when(encryptor.decrypt(encryptedValue)).thenReturn(value);
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
        acct.setPhone("111-222-3333");
        
        assertEquals("111-222-3333-encryptor2encrypted", data.get("phone"));
        assertEquals("111-222-3333", acct.getPhone());
    }
    
    @Test
    public void noValueSupported() {
        assertNull(acct.getPhone());
    }
    
    @Test
    public void oldValuesDecryptedWithOldDecryptorAndEncryptedWithNewDecryptor() {
        data.put("phone", "111-222-3333-encryptor1encrypted");
        data.put("phone_version", 1);

        assertEquals("111-222-3333", acct.getPhone());
        
        acct.setPhone(acct.getPhone());
        
        assertEquals("111-222-3333-encryptor2encrypted", data.get("phone"));
        assertEquals("111-222-3333", acct.getPhone());
    }
    
    @Test
    public void consentSignatureStoredAndEncrypted() {
        ConsentSignature sig = ConsentSignature.create("Test", "1970-01-01", "test", "image/png");
        
        acct.setConsentSignature(sig);
        
        ConsentSignature restoredSig = acct.getConsentSignature();
        assertEquals("Test", restoredSig.getName());
        assertEquals("1970-01-01", restoredSig.getBirthdate());
        assertEquals("test", restoredSig.getImageData());
        assertEquals("image/png", restoredSig.getImageMimeType());
    }
    
    @Test
    public void canClearKeyValue() {
        acct.setPhone("111-222-3333");
        acct.setPhone(null);
        
        assertNull(acct.getPhone());
    }
    
    @Test
    public void healthIdRetrievedWithNewVersion() {
        data.put("foo_code", "aHealthId-encryptor1encrypted");
        data.put("foo_code_version", 1);
        
        String healthId = acct.getHealthId();
        assertEquals("aHealthId", healthId);
    }
    
    @Test
    public void healthIdRetrievedWithOldVersion() {
        data.put("foo_code", "aHealthId-encryptor1encrypted");
        data.put("fooversion", 1);
        
        String healthId = acct.getHealthId();
        assertEquals("aHealthId", healthId);
    }
    
    @Test
    public void consentSignatureRetrievedWithNoVersion() throws Exception {
        // There is no version attribute for this. Can still retrieve it.
        data.put("foo_consent_signature", legacySignature);
        
        ConsentSignature restoredSig = acct.getConsentSignature();
        assertEquals("Test", restoredSig.getName());
        assertEquals("1970-01-01", restoredSig.getBirthdate());
        assertEquals("test", restoredSig.getImageData());
        assertEquals("image/png", restoredSig.getImageMimeType());
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
        data.put("phone", "555-555-5555-encryptor2encrypted");
        
        // This must use version 2, there's no version listed.
        String phone = acct.getPhone();
        assertEquals("555-555-5555", phone);
    }
    
    @Test
    public void phoneRetrievedWithCorrect() {
        data.put("phone", "555-555-5555-encryptor2encrypted");
        data.put("phone_version", 2);
        
        // This must use version 2, there's no version listed.
        String phone = acct.getPhone();
        assertEquals("555-555-5555", phone);
    }
    
    @Test(expected = BridgeServiceException.class)
    public void phoneNotRetrievedWithIncorrectVersion() {
        data.put("phone", "encryptedphonenumber");
        data.put("phone_version", 3);
        
        acct.getPhone();
    }
    
    @Test
    public void retrievingNullEncryptedFieldReturnsNull() {
        String phone = acct.getPhone();
        assertNull(phone);
    }
    
    @Test
    public void canSetAndGetRoles() {
        acct.getRoles().add("aRole");
        
        assertEquals(1, acct.getRoles().size());
        assertEquals("aRole", acct.getRoles().iterator().next());
    }
}
