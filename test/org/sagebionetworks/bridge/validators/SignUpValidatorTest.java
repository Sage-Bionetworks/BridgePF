package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertFalse;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;

import com.google.common.collect.Sets;

public class SignUpValidatorTest {

    private final static Set<Roles> EMPTY_ROLES = new HashSet<>();
    
    private SignUpValidator validator;
    
    @Before
    public void before() {
        validator = new SignUpValidator(new PasswordPolicy(8, true, true, true, true), Sets.newHashSet("bluebell"));
    }
    
    public void assertCorrectMessage(SignUp signUp, String fieldName, String message) {
        try {
            Validate.entityThrowingException(validator, signUp);
            fail("should have thrown exception");
        } catch(InvalidEntityException e) {
            List<String> errors = e.getErrors().get(fieldName);
            assertFalse(errors == null || errors.isEmpty());
            String error = errors.get(0);
            assertEquals(message, error);
        }
    }
    
    private SignUp withPassword(String password) {
        return new SignUp("username", "email@email.com", password, EMPTY_ROLES, null);
    }
    
    private SignUp withEmail(String email) {
        return new SignUp("username", email, "aAz1%_aAz1%", EMPTY_ROLES, null);
    }
    
    private SignUp withUsername(String username) {
        return new SignUp(username, "email@email.com", "aAz1%_aAz1%", EMPTY_ROLES, null);
    }
    
    private SignUp withDataGroup(String dataGroup) {
        return new SignUp("username", "email@email.com", "aAz1%_aAz1%", EMPTY_ROLES, Sets.newHashSet(dataGroup));
    }
    
    @Test
    public void validPasses() {
        Validate.entityThrowingException(validator, withEmail("email@email.com"));
        Validate.entityThrowingException(validator, withDataGroup("bluebell"));
    }
    
    @Test
    public void emailRequired() {
        assertCorrectMessage(withEmail(null), "email", "email is required");
    }
    
    @Test
    public void usernameRequired() {
        assertCorrectMessage(withUsername(""), "username", "username is required");
    }
    
    @Test
    public void passwordRequired() {
        assertCorrectMessage(withPassword(""), "password", "password is required");
    }
    
    @Test
    public void validEmail() {
        assertCorrectMessage(withEmail("belgium"), "email", "email must be a valid email address");
    }
    
    @Test
    public void minLength() {
        assertCorrectMessage(withPassword("a1A~"), "password", "password must be at least 8 characters");
    }
    
    @Test
    public void numberRequired() {
        assertCorrectMessage(withPassword("aaaaaaaaA~"), "password", "password must contain at least one number (0-9)");
    }
    
    @Test
    public void symbolRequired() {
        assertCorrectMessage(withPassword("aaaaaaaaA1"), "password", 
            "password must contain at least one symbol ( !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~ )");
    }
    
    @Test
    public void lowerCaseRequired() {
        assertCorrectMessage(withPassword("AAAAA!A1"), "password", "password must contain at least one lowercase letter (a-z)");
    }
    
    @Test
    public void upperCaseRequired() {
        assertCorrectMessage(withPassword("aaaaa!a1"), "password", "password must contain at least one uppercase letter (A-Z)");
    }
    
    @Test
    public void validatesDataGroupsValidIfSupplied() {
        assertCorrectMessage(withDataGroup("squirrel"), "dataGroups", "dataGroups 'squirrel' is not one of these valid values: bluebell.");
    }
    
}
