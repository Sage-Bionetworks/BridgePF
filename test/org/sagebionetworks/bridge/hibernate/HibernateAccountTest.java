package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.junit.Test;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class HibernateAccountTest {
    @Test
    public void attributes() {
        HibernateAccount account = new HibernateAccount();

        // Can set and get attributes.
        Map<String, String> originalAttrMap = new HashMap<>();
        originalAttrMap.put("foo", "foo-value");
        account.setAttributes(originalAttrMap);

        Map<String, String> gettedAttrMap1 = account.getAttributes();
        assertEquals(1, gettedAttrMap1.size());
        assertEquals("foo-value", gettedAttrMap1.get("foo"));

        // Putting values in the map reflect through to the account object.
        gettedAttrMap1.put("bar", "bar-value");

        Map<String, String> gettedAttrMap2 = account.getAttributes();
        assertEquals(2, gettedAttrMap2.size());
        assertEquals("foo-value", gettedAttrMap2.get("foo"));
        assertEquals("bar-value", gettedAttrMap2.get("bar"));

        // Setting attributes to null clears it and returns a new empty map.
        account.setAttributes(null);

        Map<String, String> gettedAttrMap3 = account.getAttributes();
        assertTrue(gettedAttrMap3.isEmpty());

        // Similarly, putting values to the map reflect through.
        gettedAttrMap3.put("baz", "baz-value");

        Map<String, String> gettedAttrMap4 = account.getAttributes();
        assertEquals(1, gettedAttrMap4.size());
        assertEquals("baz-value", gettedAttrMap4.get("baz"));
    }

    @Test
    public void consents() {
        HibernateAccount account = new HibernateAccount();

        // Create dummy consents and keys.
        HibernateAccountConsentKey fooConsentKey = new HibernateAccountConsentKey("foo-guid", 1111);
        HibernateAccountConsentKey barConsentKey = new HibernateAccountConsentKey("bar-guid", 2222);
        HibernateAccountConsentKey bazConsentKey = new HibernateAccountConsentKey("baz-guid", 3333);

        HibernateAccountConsent fooConsent = new HibernateAccountConsent();
        HibernateAccountConsent barConsent = new HibernateAccountConsent();
        HibernateAccountConsent bazConsent = new HibernateAccountConsent();

        // Can set and get.
        Map<HibernateAccountConsentKey, HibernateAccountConsent> originalConsentMap = new HashMap<>();
        originalConsentMap.put(fooConsentKey, fooConsent);
        account.setConsents(originalConsentMap);

        Map<HibernateAccountConsentKey, HibernateAccountConsent> gettedConsentMap1 = account.getConsents();
        assertEquals(1, gettedConsentMap1.size());
        assertSame(fooConsent, gettedConsentMap1.get(fooConsentKey));

        // Putting values in the map reflect through to the account object.
        gettedConsentMap1.put(barConsentKey, barConsent);

        Map<HibernateAccountConsentKey, HibernateAccountConsent> gettedConsentMap2 = account.getConsents();
        assertEquals(2, gettedConsentMap2.size());
        assertSame(fooConsent, gettedConsentMap2.get(fooConsentKey));
        assertSame(barConsent, gettedConsentMap2.get(barConsentKey));

        // Setting to null clears the map. Getting again initializes a new empty map.
        account.setConsents(null);

        Map<HibernateAccountConsentKey, HibernateAccountConsent> gettedConsentMap3 = account.getConsents();
        assertTrue(gettedConsentMap3.isEmpty());

        // Similarly, putting values to the map reflect through.
        gettedConsentMap3.put(bazConsentKey, bazConsent);

        Map<HibernateAccountConsentKey, HibernateAccountConsent> gettedConsentMap4 = account.getConsents();
        assertEquals(1, gettedConsentMap4.size());
        assertSame(bazConsent, gettedConsentMap4.get(bazConsentKey));
    }

    @Test
    public void roles() {
        HibernateAccount account = new HibernateAccount();

        // Can set and get.
        account.setRoles(EnumSet.of(Roles.ADMIN));
        Set<Roles> gettedRoleSet1 = account.getRoles();
        assertEquals(EnumSet.of(Roles.ADMIN), gettedRoleSet1);

        // Putting values in the set reflect through to the account object.
        gettedRoleSet1.add(Roles.DEVELOPER);
        assertEquals(EnumSet.of(Roles.ADMIN, Roles.DEVELOPER), account.getRoles());

        // Setting to null clears the set. Getting again initializes a new empty set.
        account.setRoles(null);
        Set<Roles> gettedRoleSet2 = account.getRoles();
        assertTrue(gettedRoleSet2.isEmpty());

        // Similarly, putting values to the set reflect through.
        gettedRoleSet2.add(Roles.RESEARCHER);
        assertEquals(EnumSet.of(Roles.RESEARCHER), account.getRoles());
    }
    
    @Test
    public void accountSummaryConstructor() {
        HibernateAccount account = new HibernateAccount(new DateTime(123L), TestConstants.TEST_STUDY_IDENTIFIER, "firstName",
                "lastName", "email", TestConstants.PHONE, "externalId", "id", AccountStatus.UNVERIFIED);

        assertEquals(123L, account.getCreatedOn().getMillis());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, account.getStudyId());
        assertEquals("firstName", account.getFirstName());
        assertEquals("lastName", account.getLastName());
        assertEquals("email", account.getEmail());
        assertEquals(TestConstants.PHONE, account.getPhone());
        assertEquals("externalId", account.getExternalId());
        assertEquals("id", account.getId());
        assertEquals(AccountStatus.UNVERIFIED, account.getStatus());
    }
    
    @Test
    public void dataGroups() {
        HibernateAccount account = new HibernateAccount();
        assertTrue(account.getDataGroups().isEmpty());

        // Set works.
        account.setDataGroups(Sets.newHashSet("A","B"));
        assertEquals(Sets.newHashSet("A","B"), account.getDataGroups());

        // Setting to null makes it an empty set.
        account.setDataGroups(null);
        assertTrue(account.getDataGroups().isEmpty());

        account.setDataGroups(Sets.newHashSet());
        assertTrue(account.getRoles().isEmpty());
    }
    
    @Test
    public void languages() {
        // Hibernate cannot deal with the LinkedHashSet type, so we store languages as a list to 
        // maintain their order. When transferred to an object implementing the Account class, these
        // are transferred to a LinkedHashSet, which also ensures the language codes are not 
        // duplicated in the ordered set.
        List<String> langs = Lists.newArrayList("en","fr");
        
        HibernateAccount account = new HibernateAccount();
        assertTrue(account.getLanguages().isEmpty());
        
        // Set works.
        account.setLanguages(langs);
        assertEquals(langs, account.getLanguages());
        
        // Setting to null makes it an empty set.
        account.setLanguages(null);
        assertTrue(account.getLanguages().isEmpty());
    }
    
    @Test
    public void sharingDefaultsToNoSharing() {
        HibernateAccount account = new HibernateAccount();
        assertEquals(SharingScope.NO_SHARING, account.getSharingScope());
    }
    
    @Test
    public void getNotifyByEmailDefaultsToTrue() {
        HibernateAccount account = new HibernateAccount();
        assertTrue(account.getNotifyByEmail());
    }

    @Test
    public void consentSignatureHistories() {
        long TIME1 = 100L;
        long TIME2 = 200L;
        long TIME3 = 300L;
        long TIME4 = 400L;
        long TIME5 = 500L;
        
        SubpopulationGuid guid1 = SubpopulationGuid.create("guid1");
        SubpopulationGuid guid2 = SubpopulationGuid.create("guid2");
        
        HibernateAccountConsentKey key1A = new HibernateAccountConsentKey(guid1.getGuid(), TIME1);
        HibernateAccountConsentKey key1B = new HibernateAccountConsentKey(guid1.getGuid(), TIME2);
        HibernateAccountConsentKey key1C = new HibernateAccountConsentKey(guid1.getGuid(), TIME3);
        HibernateAccountConsentKey key2A = new HibernateAccountConsentKey(guid2.getGuid(), TIME4);
        HibernateAccountConsentKey key2B = new HibernateAccountConsentKey(guid2.getGuid(), TIME5);
        
        HibernateAccountConsent consent1A = getHibernateAccountConsent(null);
        HibernateAccountConsent consent1B = getHibernateAccountConsent(null);
        HibernateAccountConsent consent1C = getHibernateAccountConsent(400L);
        HibernateAccountConsent consent2A = getHibernateAccountConsent(null);
        HibernateAccountConsent consent2B = getHibernateAccountConsent(null);
        
        // Add these out of order to verify that they are sorted by date of signing
        Map<HibernateAccountConsentKey, HibernateAccountConsent> consents = Maps.newHashMap();
        consents.put(key1A, consent1A);
        consents.put(key1C, consent1C);
        consents.put(key1B, consent1B);
        consents.put(key2B, consent2B);
        consents.put(key2A, consent2A);
        
        HibernateAccount account = new HibernateAccount();
        account.setConsents(consents);
        
        // Test getAllConsentSignaturehistories()
        Map<SubpopulationGuid, List<ConsentSignature>> histories = account.getAllConsentSignatureHistories();
        
        List<ConsentSignature> history1 = histories.get(guid1);
        assertEquals(3, history1.size());
        // Signed on values are copied over from keys
        assertEquals(TIME1, history1.get(0).getSignedOn());
        assertEquals(TIME2, history1.get(1).getSignedOn());
        assertEquals(TIME3, history1.get(2).getSignedOn());
        
        List<ConsentSignature> history2 = histories.get(guid2);
        assertEquals(2, history2.size());
        // Signed on values are copied over from keys
        assertEquals(TIME4, history2.get(0).getSignedOn());
        assertEquals(TIME5, history2.get(1).getSignedOn());
        
        // Test getConsentSignatureHistory(guid). Should produce identical results.
        history1 = account.getConsentSignatureHistory(guid1);
        assertEquals(3, history1.size());
        // Signed on values are copied over from keys
        assertEquals(TIME1, history1.get(0).getSignedOn());
        assertEquals(TIME2, history1.get(1).getSignedOn());
        assertEquals(TIME3, history1.get(2).getSignedOn());
        
        history2 = account.getConsentSignatureHistory(guid2);
        assertEquals(2, history2.size());
        // Signed on values are copied over from keys
        assertEquals(TIME4, history2.get(0).getSignedOn());
        assertEquals(TIME5, history2.get(1).getSignedOn());
        
        // The last consent in the series was withdrawn, so this consent is not active.
        ConsentSignature sig1 = account.getActiveConsentSignature(guid1);
        assertNull(sig1);
        
        ConsentSignature sig2 = account.getActiveConsentSignature(guid2);
        assertEquals(sig2, history2.get(1));
        
        // Add a consent to the withdrawn series.
        ConsentSignature sig3 = new ConsentSignature.Builder().withBirthdate("1980-01-01")
                .withConsentCreatedOn(1L).withName("Name").withSignedOn(600L).build();
        
        List<ConsentSignature> signatures = Lists.newArrayList();
        signatures.addAll(history1);
        signatures.add(sig3);
        account.setConsentSignatureHistory(guid1, signatures);
        
        sig1 = account.getActiveConsentSignature(guid1);
        assertEquals(sig1, account.getAllConsentSignatureHistories().get(guid1).get(3));
    }
    
    @Test
    public void collectionsNotNull() {
        HibernateAccount account = new HibernateAccount();
        assertTrue(account.getAccountSubstudies().isEmpty());
        assertTrue(account.getAttributes().isEmpty());
        assertTrue(account.getConsents().isEmpty());
        assertTrue(account.getDataGroups().isEmpty());
        assertTrue(account.getLanguages().isEmpty());
        assertTrue(account.getRoles().isEmpty());
        assertTrue(account.getConsentSignatureHistory(SubpopulationGuid.create("nada")).isEmpty());
        assertTrue(account.getAllConsentSignatureHistories().isEmpty());
    }
    
    private HibernateAccountConsent getHibernateAccountConsent(Long withdrewOn) {
        HibernateAccountConsent consent = new HibernateAccountConsent();
        consent.setBirthdate("1980-01-01");
        consent.setConsentCreatedOn(1L);
        consent.setName("Test User");
        consent.setWithdrewOn(withdrewOn);
        return consent;
    }
    
}
