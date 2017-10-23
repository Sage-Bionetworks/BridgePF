package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.accounts.VerificationToken;

public class VerificationTokenValidatorTest {
    @Test
    public void verifyToken() {
        VerificationToken token = new VerificationToken(null,"token");
        Validate.entityThrowingException(VerificationTokenValidator.INSTANCE, token);
    }

    @Test
    public void verifySpToken() {
        VerificationToken token = new VerificationToken("sptoken",null);
        Validate.entityThrowingException(VerificationTokenValidator.INSTANCE, token);
    }

    @Test
    public void tokenTakesPrecedence() {
        VerificationToken token = new VerificationToken("sptoken", "token");
        assertEquals("token", token.getToken());
    }
    
    @Test
    public void testTokenRequired() {
        VerificationToken token = new VerificationToken(null, null);
        TestUtils.assertValidatorMessage(VerificationTokenValidator.INSTANCE, token, "token", "is required");
    }
}
