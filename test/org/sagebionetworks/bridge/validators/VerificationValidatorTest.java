package org.sagebionetworks.bridge.validators;

import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.accounts.Verification;

public class VerificationValidatorTest {
    @Test
    public void verifySpToken() {
        Verification token = new Verification("sptoken");
        Validate.entityThrowingException(VerificationValidator.INSTANCE, token);
    }

    @Test
    public void testTokenRequired() {
        Verification token = new Verification(null);
        TestUtils.assertValidatorMessage(VerificationValidator.INSTANCE, token, "sptoken", "is required");
    }
}
