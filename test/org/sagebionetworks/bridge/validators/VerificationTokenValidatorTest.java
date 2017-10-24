package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;

public class VerificationTokenValidatorTest {
    @Test
    public void verifyToken() {
        EmailVerification token = new EmailVerification(null,"token");
        Validate.entityThrowingException(VerificationTokenValidator.INSTANCE, token);
    }

    @Test
    public void verifySpToken() {
        EmailVerification token = new EmailVerification("sptoken",null);
        Validate.entityThrowingException(VerificationTokenValidator.INSTANCE, token);
    }

    @Test
    public void tokenTakesPrecedence() {
        EmailVerification token = new EmailVerification("sptoken", "token");
        assertEquals("token", token.getSpToken());
    }
    
    @Test
    public void testTokenRequired() {
        EmailVerification token = new EmailVerification(null, null);
        TestUtils.assertValidatorMessage(VerificationTokenValidator.INSTANCE, token, "token", "is required");
    }
}
