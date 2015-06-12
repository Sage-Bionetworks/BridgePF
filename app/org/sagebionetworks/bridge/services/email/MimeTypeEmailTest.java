package org.sagebionetworks.bridge.services.email;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.junit.Test;

import com.google.common.collect.Lists;

public class MimeTypeEmailTest {

    private MimeTypeEmail makeEmailWithSender(String sender) throws MessagingException {
        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setText("", StandardCharsets.UTF_8.name(), "text/plain");
        String recipient = "bridge-testing@sagebase.org";
        return new MimeTypeEmail("subject", sender, Lists.<String> newArrayList(recipient), Lists.<MimeBodyPart> newArrayList(bodyPart));
    }
    
    private MimeTypeEmail makeEmailWithRecipient(String recipient) throws MessagingException {
        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setText("", StandardCharsets.UTF_8.name(), "text/plain");
        String sender = "bridge-testing@sagebase.org";
        return new MimeTypeEmail("subject", sender, Lists.<String> newArrayList(recipient), Lists.<MimeBodyPart> newArrayList(bodyPart));
    }
    
    @Test
    public void senderUnadornedEmailNotChanged() throws Exception {
        MimeTypeEmail email = makeEmailWithSender("test@test.com");
        assertEquals("test@test.com", email.getSenderAddress());
    }
    
    @Test
    public void senderAddressWithNameQuoted() throws Exception {
        MimeTypeEmail email = makeEmailWithSender("A, B, and C <test@test.com>");
        assertEquals("\"A, B, and C\" <test@test.com>", email.getSenderAddress());
    }
    
    @Test
    public void senderAddressWithNameWithQuotesItsAllQuoted() throws Exception {
        MimeTypeEmail email = makeEmailWithSender("The \"Fun Guys\" at UofW <test@test.com>");
        assertEquals("\"The \\\"Fun Guys\\\" at UofW\" <test@test.com>", email.getSenderAddress());
    }

    @Test
    public void recipientUnadornedEmailNotChanged() throws Exception {
        MimeTypeEmail email = makeEmailWithRecipient("test@test.com");
        assertEquals("test@test.com", email.getRecipientAddresses().get(0));
    }
    
    @Test
    public void recipientAddressWithNameQuoted() throws Exception {
        MimeTypeEmail email = makeEmailWithRecipient("A, B, and C <test@test.com>");
        assertEquals("\"A, B, and C\" <test@test.com>", email.getRecipientAddresses().get(0));
    }
    
    @Test
    public void recipientAddressWithNameWithQuotesItsAllQuoted() throws Exception {
        MimeTypeEmail email = makeEmailWithRecipient("The \"Fun Guys\" at UofW <test@test.com>");
        assertEquals("\"The \\\"Fun Guys\\\" at UofW\" <test@test.com>", email.getRecipientAddresses().get(0));
    }
}
