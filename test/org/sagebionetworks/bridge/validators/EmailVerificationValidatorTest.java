package org.sagebionetworks.bridge.validators;

import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;

public class EmailVerificationValidatorTest {
    @Test
    public void verifySpToken() {
        EmailVerification token = new EmailVerification("sptoken");
        Validate.entityThrowingException(EmailVerificationValidator.INSTANCE, token);
    }

    @Test
    public void testTokenRequired() {
        EmailVerification token = new EmailVerification(null);
        TestUtils.assertValidatorMessage(EmailVerificationValidator.INSTANCE, token, "sptoken", "is required");
    }
}
