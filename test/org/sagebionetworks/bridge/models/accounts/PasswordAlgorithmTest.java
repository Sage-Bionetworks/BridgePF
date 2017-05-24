package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

public class PasswordAlgorithmTest {
    private static final String TEST_PASSWORD = "People use 'password1', which is a bad password.";
    private static final String WRONG_PASSWORD = "This is the wrong password.";

    @Test
    public void stormpath() throws Exception {
        test(PasswordAlgorithm.STORMPATH_HMAC_SHA_256);
    }

    @Test
    public void bcrypt() throws Exception {
        test(PasswordAlgorithm.BCRYPT);
    }

    @Test
    public void pbkdf2() throws Exception {
        test(PasswordAlgorithm.PBKDF2_HMAC_SHA_256);
    }

    private static void test(PasswordAlgorithm passwordAlgorithm) throws Exception {
        String hash = passwordAlgorithm.generateHash(TEST_PASSWORD);
        assertTrue(StringUtils.isNotBlank(hash));
        assertTrue(passwordAlgorithm.checkHash(hash, TEST_PASSWORD));
        assertFalse(passwordAlgorithm.checkHash(hash, WRONG_PASSWORD));
    }
}
