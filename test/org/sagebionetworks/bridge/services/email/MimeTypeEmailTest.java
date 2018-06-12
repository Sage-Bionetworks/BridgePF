package org.sagebionetworks.bridge.services.email;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class MimeTypeEmailTest {
    private static final String SUBJECT = "subject";

    private static MimeTypeEmail makeEmailWithSender(String sender) throws MessagingException {
        String recipient = "bridge-testing@sagebase.org";
        return new MimeTypeEmailBuilder().withSubject(SUBJECT).withSender(sender).withRecipient(recipient)
                .withMessageParts(makeBodyPart("dummy content")).withType(EmailType.EMAIL_SIGN_IN).build();
    }
    
    private static MimeTypeEmail makeEmailWithRecipient(String recipient) throws MessagingException {
        String sender = "bridge-testing@sagebase.org";
        return new MimeTypeEmailBuilder().withSubject(SUBJECT).withSender(sender).withRecipient(recipient)
                .withMessageParts(makeBodyPart("dummy content")).withType(EmailType.EMAIL_SIGN_IN).build();
    }

    private static MimeBodyPart makeBodyPart(String content) throws MessagingException {
        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setText(content, StandardCharsets.UTF_8.name(), "text/plain");
        return bodyPart;
    }

    @Test
    public void testAttributes() throws Exception {
        MimeTypeEmail email = makeEmailWithSender("test@example.com");
        assertEquals(SUBJECT, email.getSubject());
        assertEquals(EmailType.EMAIL_SIGN_IN, email.getType());

        assertEquals(1, email.getMessageParts().size());
        assertEquals("dummy content", email.getMessageParts().get(0).getContent());
    }

    @Test
    public void senderUnadornedEmailNotChanged() throws Exception {
        MimeTypeEmail email = makeEmailWithSender("test@test.com");
        assertEquals("test@test.com", email.getSenderAddress());
    }
    
    @Test
    public void senderOddlyFormattedButLegal() throws Exception {
        MimeTypeEmail email = makeEmailWithSender("<test@test.com>");
        assertEquals("<test@test.com>", email.getSenderAddress());
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

    @Test
    public void multipleRecipients() throws Exception {
        List<String> recipientList = ImmutableList.of("recipient1@example.com", "recipient2@example.com",
                "recipient3@example.com");
        MimeTypeEmail email = new MimeTypeEmailBuilder().withSubject(SUBJECT).withSender("sender@example.com")
                .withRecipients(recipientList).withMessageParts(makeBodyPart("dummy content"))
                .withType(EmailType.EMAIL_SIGN_IN).build();
        assertEquals(recipientList, email.getRecipientAddresses());
    }

    @Test(expected = IllegalArgumentException.class)
    public void filterOutNullMessagePartVarargs() {
        // This will throw an IllegalArgumentException, since we don't allow null message parts list.
        new MimeTypeEmailBuilder().withSubject(SUBJECT).withSender("sender@example.com")
                .withRecipient("recipient@example.com").withMessageParts((MimeBodyPart[]) null)
                .withType(EmailType.EMAIL_SIGN_IN).build();
    }

    @Test
    public void filterOutNullMessageParts() throws Exception {
        MimeTypeEmail email = new MimeTypeEmailBuilder().withSubject(SUBJECT).withSender("sender@example.com")
                .withRecipient("recipient@example.com")
                .withMessageParts(null, makeBodyPart("foo"), null, makeBodyPart("bar"), null)
                .withType(EmailType.EMAIL_SIGN_IN).build();

        List<MimeBodyPart> partList = email.getMessageParts();
        assertEquals(2, partList.size());
        assertEquals("foo", partList.get(0).getContent());
        assertEquals("bar", partList.get(1).getContent());
    }

    @Test
    public void defaultType() throws Exception {
        MimeTypeEmail email = new MimeTypeEmailBuilder().withSubject(SUBJECT).withSender("sender@example.com")
                .withRecipient("recipient@example.com").withMessageParts(makeBodyPart("dummy content"))
                .withType(null).build();
        assertEquals(EmailType.UNKNOWN, email.getType());
    }
}
