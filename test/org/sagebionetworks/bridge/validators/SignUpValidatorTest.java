package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;

public class SignUpValidatorTest {

    private final static Set<Roles> EMPTY_ROLES = new HashSet<>();
    
    private SignUpValidator validator;
    
    @Before
    public void before() {
        validator = new SignUpValidator(new PasswordPolicy(8, true, true, true, true));
    }
    
    private SignUp withPassword(String password) {
        return new SignUp("username", "email@email.com", password, EMPTY_ROLES);
    }
    
    private SignUp withEmail(String email) {
        return new SignUp("username", email, "aAz1%_aAz1%", EMPTY_ROLES);
    }
    
    private SignUp withUsername(String username) {
        return new SignUp(username, "email@email.com", "aAz1%_aAz1%", EMPTY_ROLES);
    }
    
    private String errorFor(InvalidEntityException e, String fieldName) {
        Map<String,List<String>> errors = e.getErrors();
        assertNotNull(errors);
        List<String> messages = errors.get(fieldName);
        assertFalse(messages == null || messages.isEmpty());
        return messages.get(0);
    }
    
    @Test
    public void validPasses() {
        Validate.entityThrowingException(validator, withEmail("email@email.com"));
    }
    
    @Test
    public void emailRequired() {
        try {
            Validate.entityThrowingException(validator, withEmail(null));
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("email is required", errorFor(e, "email"));
        }
    }
    
    @Test
    public void usernameRequired() {
        try {
            Validate.entityThrowingException(validator, withUsername(""));
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("username is required", errorFor(e, "username"));
        }
    }
    
    @Test
    public void passwordRequired() {
        try {
            Validate.entityThrowingException(validator, withPassword(""));
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("password is required", errorFor(e, "password"));
        }
    }
    
    @Test
    public void validEmail() {
        try {
            Validate.entityThrowingException(validator, withEmail("belgium"));
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("email must be a valid email address", errorFor(e, "email"));
        }
    }
    
    @Test
    public void minLength() {
        try {
            Validate.entityThrowingException(validator, withPassword("a1A~"));
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("password must be at least 8 characters", errorFor(e, "password"));
        }
    }
    
    @Test
    public void numberRequired() {
        try {
            Validate.entityThrowingException(validator, withPassword("aaaaaaaaA~"));
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("password must contain at least one number (0-9)", errorFor(e, "password"));
        }
    }
    
    @Test
    public void symbolRequired() {
        try {
            Validate.entityThrowingException(validator, withPassword("aaaaaaaaA1"));
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("password must contain at least one symbol ( !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~ )", errorFor(e, "password"));
        }
    }
    
    @Test
    public void lowerCaseRequired() {
        try {
            Validate.entityThrowingException(validator, withPassword("AAAAA!A1"));
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("password must contain at least one lowercase letter (a-z)", errorFor(e, "password"));
        }
    }
    
    @Test
    public void upperCaseRequired() {
        try {
            Validate.entityThrowingException(validator, withPassword("aaaaa!a1"));
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("password must contain at least one uppercase letter (A-Z)", errorFor(e, "password"));
        }
    }
}
