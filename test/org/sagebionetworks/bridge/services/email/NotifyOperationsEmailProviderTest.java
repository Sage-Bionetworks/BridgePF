package org.sagebionetworks.bridge.services.email;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;

public class NotifyOperationsEmailProviderTest {

    private String sysopsEmail;
    private NotifyOperationsEmailProvider provider;
    
    @Before
    public void before() {
        // This is a quick and dirty implementation of the escaping that is done
        // by the email service, so we can test without hardcoding the actual email.
        sysopsEmail = BridgeConfigFactory.getConfig().getProperty("sysops.email");
        String[] parts = sysopsEmail.split(" <");
        sysopsEmail = "\"" + parts[0] + "\" <" + parts[1];
    }
    
    @Test
    public void testWithNoSender() throws Exception {
        provider = new NotifyOperationsEmailProvider("Subject", "This is a test message.");
        
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals(sysopsEmail, email.getSenderAddress());
        assertEquals(sysopsEmail, email.getRecipientAddresses().get(0));
        assertEquals("Subject", email.getSubject());
        assertEquals("This is a test message.", (String)email.getMessageParts().get(0).getContent());
    }

}
