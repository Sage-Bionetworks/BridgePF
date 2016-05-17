package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.HashMap;
import java.util.Set;

import org.joda.time.DateTime;
import org.junit.Test;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class UserTest {

    private static final DateTime CREATED_ON = DateTime.now();
    private static final Set<Roles> ROLES = Sets.newHashSet(Roles.ADMIN, Roles.DEVELOPER);
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(User.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void initializedFromAccount() {
        Account account = mock(Account.class);
        doReturn("email.email.com").when(account).getEmail();
        doReturn("first").when(account).getFirstName();
        doReturn("last").when(account).getLastName();
        doReturn("id").when(account).getId();
        doReturn(CREATED_ON).when(account).getCreatedOn();
        doReturn(ROLES).when(account).getRoles();
        
        User user = new User(account);
        assertEquals("email.email.com", user.getEmail());
        assertEquals("first", user.getFirstName());
        assertEquals("last", user.getLastName());
        assertEquals("id", user.getId());
        assertEquals(CREATED_ON, user.getAccountCreatedOn());
        assertEquals(ROLES, user.getRoles());
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
        user.setConsentStatuses(TestUtils.toMap(
            new ConsentStatus("Name", "guid1", true, true, false),
            new ConsentStatus("Name", "guid2", true, true, true),
            new ConsentStatus("Name", "guid3", false, false, false)
        ));
        assertTrue(user.doesConsent());
        
        // A required consent is not consented
        user = new User();
        user.setConsentStatuses(TestUtils.toMap(
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
        user.setConsentStatuses(TestUtils.toMap(
            new ConsentStatus("Name", "guid1", true, true, false),
            new ConsentStatus("Name", "guid2", true, true, true),
            new ConsentStatus("Name", "guid3", false, false, false)
        ));
        assertFalse(user.hasSignedMostRecentConsent());
        
        // A required consent is not consented
        user = new User();
        user.setConsentStatuses(TestUtils.toMap(
            new ConsentStatus("Name", "guid1", true, true, false),
            new ConsentStatus("Name", "guid2", true, false, false),
            new ConsentStatus("Name", "guid3", false, false, false)
        ));
        assertFalse(user.hasSignedMostRecentConsent());
        
        user = new User();
        user.setConsentStatuses(TestUtils.toMap(
            new ConsentStatus("Name", "guid1", true, true, true),
            new ConsentStatus("Name", "guid3", false, false, false)
        ));
        // Again, we don't count optional consents, only required consents.
        assertTrue(user.hasSignedMostRecentConsent());
    }
}
