package org.sagebionetworks.bridge.services.email;

import java.util.Collection;
import java.util.List;

import javax.mail.internet.MimeBodyPart;

import com.google.common.collect.Lists;

class MimeTypeEmailBuilder {
    private String subject;
    private String senderAddress;
    private List<String> recipientAddresses = Lists.newArrayList();
    private List<MimeBodyPart> messageParts = Lists.newArrayList();
    private EmailType type;
    
    /**
     * The subject of the email. Required.
     */
    MimeTypeEmailBuilder withSubject(String subject) {
        this.subject = subject;
        return this;
    }
    /**
     * The sender of the email. This value is optional (if not supplied, the server's 
     * default support email address will be used).
     */
    MimeTypeEmailBuilder withSender(String senderAddress) {
        this.senderAddress = senderAddress;
        return this;
    }
    /**
     * One or more recipients for this email (may call this more than once and values will be accumulated).
     */
    MimeTypeEmailBuilder withRecipients(Collection<String> recipients) {
        this.recipientAddresses.addAll(recipients);
        return this;
    }
    /**
     * A recipient for this email (may call this more than once and values will be accumulated).
     */
    MimeTypeEmailBuilder withRecipient(String recipient) {
        this.recipientAddresses.add(recipient);
        return this;
    }
    /**
     * A body part for a MIME-based email message (may call this more than once and the body parts 
     * will be accumulated).
     */
    MimeTypeEmailBuilder withMessageParts(MimeBodyPart... parts) {
        if (parts != null) {
            for (MimeBodyPart part : parts) {
                if (part != null) {
                    messageParts.add(part);    
                }
            }
        }
        return this;
    }

    /** @see MimeTypeEmail#getType */
    MimeTypeEmailBuilder withType(EmailType type) {
        this.type = type;
        return this;
    }

    /**
     * Construct this MimeTypeEmail instance.
     */
    MimeTypeEmail build() {
        if (type == null) {
            type = EmailType.UNKNOWN;
        }

        return new MimeTypeEmail(subject, senderAddress, recipientAddresses, messageParts, type);
    }
}
