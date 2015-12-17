package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Set;

import org.junit.Test;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class UserTest {

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(User.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void cannotExposeHealthCode() throws Exception {
        User user = new User();
        user.setHealthCode("123abc");
        
        String json = BridgeObjectMapper.get().writeValueAsString(user);
        assertFalse(json.contains("123abc"));
        assertFalse(user.toString().contains("123abc"));
    }
    
    @Test
    public void testHealthCodeEncryption() throws IOException {
        User user = new User();
        user.setEmail("userEmail");
        user.setId("userId");
        user.setHealthCode("123abc");
        String userSer = BridgeObjectMapper.get().writeValueAsString(user);
        assertNotNull(userSer);
        assertFalse("Health code should have been encrypted in the serialized string.",
                userSer.toLowerCase().contains("123abc"));
        User userDe = BridgeObjectMapper.get().readValue(userSer, User.class);
        assertNotNull(userDe);
        assertEquals("123abc", userDe.getHealthCode());
    }
    
    @Test
    public void userIsInRole() {
        User user = new User();
        user.setRoles(Sets.newHashSet(Roles.ADMIN, Roles.DEVELOPER));

        assertTrue(user.isInRole(Roles.DEVELOPER));
        assertFalse(user.isInRole((Roles)null));
    }
    
    @Test
    public void immutableConsentStatuses() {
        User user = new User();
        assertTrue(user.getConsentStatuses() instanceof ImmutableMap);
        
        user.setConsentStatuses(new HashMap<>());
        assertTrue(user.getConsentStatuses() instanceof ImmutableMap);
    }
    
    @Test
    public void userIsInRoleSet() {
        User user = new User();
        user.setRoles(Sets.newHashSet(Roles.ADMIN, Roles.DEVELOPER));
        assertTrue(user.isInRole(Roles.ADMINISTRATIVE_ROLES));
        assertFalse(user.isInRole((Set<Roles>)null));
        
        user = new User();
        assertFalse(user.isInRole(Roles.ADMINISTRATIVE_ROLES));
    }
    
    @Test
    public void noConsentsProperlySetsBooleans() {
        User user = new User();
        assertFalse(user.doesConsent());
        assertFalse(user.hasSignedMostRecentConsent());
    }
    
    @Test
    public void hasUserConsentedWorks() {
        // Empty consent list... you are not considered consented
        User user = new User();
        user.setConsentStatuses(new HashMap<>());
        assertFalse(user.doesConsent());
        
        // All required consents are consented, even one that's not up-to-date
        user = new User();
        user.setConsentStatuses(ConsentStatus.toMap(
            new ConsentStatus("Name", "guid1", true, true, false),
            new ConsentStatus("Name", "guid2", true, true, true),
            new ConsentStatus("Name", "guid3", false, false, false)
        ));
        assertTrue(user.doesConsent());
        
        // A required consent is not consented
        user = new User();
        user.setConsentStatuses(ConsentStatus.toMap(
            new ConsentStatus("Name", "guid1", true, true, false),
            new ConsentStatus("Name", "guid2", true, false, false),
            new ConsentStatus("Name", "guid3", false, false, false)
        ));
        assertFalse(user.doesConsent());
    }
    
    @Test
    public void areConsentsUpToDateWorks() {
        // Empty consent list... you are not considered consented
        User user = new User();
        user.setConsentStatuses(new HashMap<>());
        assertFalse(user.hasSignedMostRecentConsent());
        
        // All required consents are consented, even one that's not up-to-date
        user = new User();
        user.setConsentStatuses(ConsentStatus.toMap(
            new ConsentStatus("Name", "guid1", true, true, false),
            new ConsentStatus("Name", "guid2", true, true, true),
            new ConsentStatus("Name", "guid3", false, false, false)
        ));
        assertFalse(user.hasSignedMostRecentConsent());
        
        // A required consent is not consented
        user = new User();
        user.setConsentStatuses(ConsentStatus.toMap(
            new ConsentStatus("Name", "guid1", true, true, false),
            new ConsentStatus("Name", "guid2", true, false, false),
            new ConsentStatus("Name", "guid3", false, false, false)
        ));
        assertFalse(user.hasSignedMostRecentConsent());
        
        user = new User();
        user.setConsentStatuses(ConsentStatus.toMap(
            new ConsentStatus("Name", "guid1", true, true, true),
            new ConsentStatus("Name", "guid3", false, false, false)
        ));
        // Again, we don't count optional consents, only required consents.
        assertTrue(user.hasSignedMostRecentConsent());
    }
}
