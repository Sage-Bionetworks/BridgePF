package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;

public class SignUpValidatorTest {

    private static final HashSet<Roles> EMPTY_ROLES = new HashSet<Roles>();
    
    private PasswordPolicy policy;
    
    private SignUpValidator validator;
    
    @Before
    public void before() {
        // Everything is required
        policy = new PasswordPolicy(8, true, true, true, true);
        validator = new SignUpValidator(policy);
    }
    
    private String errorFor(InvalidEntityException e, String field) {
        List<String> errors = e.getErrors().get(field);
        assertTrue(errors.size() > 0);
        return errors.get(0);
    }
    
    @Test
    public void minPasswordLength() {
        try {
            SignUp signUp = new SignUp("username", "email@email.com", "aB1~", EMPTY_ROLES);        
            Validate.entityThrowingException(validator, signUp);
            fail("Should have thrown exception.");
        } catch(InvalidEntityException e) {
            assertEquals("password must be at least 8 characters", errorFor(e, "password"));
        }
    }
    
    @Test
    public void numericRequired() {
        try {
            SignUp signUp = new SignUp("username", "email@email.com", "aBaaaaaaa~", EMPTY_ROLES);        
            Validate.entityThrowingException(validator, signUp);
            fail("Should have thrown exception.");
        } catch(InvalidEntityException e) {
            assertEquals("password must have at least one number", errorFor(e, "password"));
        }        
    }
    
    @Test
    public void symbolicRequired() {
        try {
            SignUp signUp = new SignUp("username", "email@email.com", "aBaa2aa1", EMPTY_ROLES);
            Validate.entityThrowingException(validator, signUp);
            fail("Should have thrown exception.");
        } catch(InvalidEntityException e) {
            assertEquals("password must have at least one symbol (! \" # $ % & ' ( ) * + , - . / : ; < = > ? @ [ \\ ] ^ _ ` { ¦ } ~)", errorFor(e, "password"));
        }        
    }
    
    @Test
    public void lowerCaseRequired() {
        try {
            SignUp signUp = new SignUp("username", "email@email.com", "ABAA2AA1~", EMPTY_ROLES);
            Validate.entityThrowingException(validator, signUp);
            fail("Should have thrown exception.");
        } catch(InvalidEntityException e) {
            assertEquals("password must have at least one lowercase letter (a-z)", errorFor(e, "password"));
        }        
    }
    
    @Test
    public void upperCaseRequired() {
        try {
            SignUp signUp = new SignUp("username", "email@email.com", "abaa2aa1~", EMPTY_ROLES);
            Validate.entityThrowingException(validator, signUp);
            fail("Should have thrown exception.");
        } catch(InvalidEntityException e) {
            assertEquals("password must have at least one uppercase letter (A-Z)", errorFor(e, "password"));
        }        
    }
    
}
