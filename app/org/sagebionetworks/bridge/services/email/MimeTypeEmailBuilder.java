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
    
    /**
     * The subject of the email. Required.
     * @param subject
     * @return
     */
    MimeTypeEmailBuilder withSubject(String subject) {
        this.subject = subject;
        return this;
    }
    /**
     * The sender of the email. This value is optional (if not supplied, the server's 
     * default support email address will be used).
     * @param senderAddress
     * @return
     */
    MimeTypeEmailBuilder withSender(String senderAddress) {
        this.senderAddress = senderAddress;
        return this;
    }
    /**
     * One or more recipients for this email (may call this more than once and values will be accumulated).
     * @param recipientAddress
     * @return
     */
    MimeTypeEmailBuilder withRecipients(Collection<String> recipients) {
        this.recipientAddresses.addAll(recipients);
        return this;
    }
    /**
     * A recipient for this email (may call this more than once and values will be accumulated).
     * @param recipientAddress
     * @return
     */
    MimeTypeEmailBuilder withRecipient(String recipient) {
        this.recipientAddresses.add(recipient);
        return this;
    }
    /**
     * A body part for a MIME-based email message (may call this more than once and the body parts 
     * will be accumulated).
     * @param parts
     * @return
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
    /**
     * Construct this MimeTypeEmail instance.
     * @return
     */
    MimeTypeEmail build() {
        return new MimeTypeEmail(subject, senderAddress, recipientAddresses, messageParts);
    }

}
