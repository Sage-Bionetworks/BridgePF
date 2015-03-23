package org.sagebionetworks.bridge.services.email;

import java.util.List;

import javax.mail.internet.MimeBodyPart;

import com.google.common.collect.Lists;

class MimeTypeEmailBuilder {

    private String subject;
    private String senderAddress;
    private List<String> recipientAddresses = Lists.newArrayList();
    private List<MimeBodyPart> messageParts = Lists.newArrayList();
    
    MimeTypeEmailBuilder withSubject(String subject) {
        this.subject = subject;
        return this;
    }
    MimeTypeEmailBuilder withSender(String senderAddress) {
        this.senderAddress = senderAddress;
        return this;
    }
    MimeTypeEmailBuilder withRecipient(String recipientAddress) {
        this.recipientAddresses.add(recipientAddress);
        return this;
    }
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
    MimeTypeEmail build() {
        return new MimeTypeEmail(subject, senderAddress, recipientAddresses, messageParts);
    }

}
