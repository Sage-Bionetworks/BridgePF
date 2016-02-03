package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.services.EmailVerificationStatus.PENDING;
import static org.sagebionetworks.bridge.services.EmailVerificationStatus.UNVERIFIED;
import static org.sagebionetworks.bridge.services.EmailVerificationStatus.VERIFIED;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EmailVerificationStatusTest {

    @Test
    public void testStringsConvertToTypes() {
        assertEquals(PENDING, EmailVerificationStatus.fromSesVerificationStatus("Pending"));
        assertEquals(VERIFIED, EmailVerificationStatus.fromSesVerificationStatus("Success"));
        assertEquals(UNVERIFIED, EmailVerificationStatus.fromSesVerificationStatus("Anything Else"));
        assertEquals(PENDING, EmailVerificationStatus.fromSesVerificationStatus(EmailVerificationStatus.PENDING.name()));
        assertEquals(VERIFIED, EmailVerificationStatus.fromSesVerificationStatus(EmailVerificationStatus.VERIFIED.name()));
        assertEquals(UNVERIFIED, EmailVerificationStatus.fromSesVerificationStatus(EmailVerificationStatus.UNVERIFIED.name()));
        assertEquals(UNVERIFIED, EmailVerificationStatus.fromSesVerificationStatus(null));
    }
    
}
