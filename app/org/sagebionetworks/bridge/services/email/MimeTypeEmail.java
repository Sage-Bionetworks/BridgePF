package org.sagebionetworks.bridge.services.email;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;

import javax.mail.internet.MimeBodyPart;

import com.google.common.collect.ImmutableList;

/**
 * This is a light wrapper around the information needed to construct a MIME multi-part email 
 * message using the Java mail API.
 */
public final class MimeTypeEmail {

    private final String subject;
    private final String senderAddress;
    private final List<String> recipientAddresses;
    private final List<MimeBodyPart> messageParts;
    
    MimeTypeEmail(String subject, String senderAddress, List<String> recipientAddresses, List<MimeBodyPart> messageParts) {
        checkArgument(isNotBlank(senderAddress));
        checkArgument(isNotBlank(subject));
        checkArgument(isNotBlank(senderAddress));
        checkArgument(recipientAddresses != null && !recipientAddresses.isEmpty());
        checkArgument(messageParts != null && !messageParts.isEmpty());
        
        this.subject = subject;
        this.senderAddress = senderAddress;
        this.recipientAddresses = recipientAddresses;
        this.messageParts = ImmutableList.copyOf(messageParts);
    }
    
    public String getSubject() {
        return subject;
    }
    public String getSenderAddress() {
        return senderAddress;
    }
    public List<String> getRecipientAddresses() {
        return recipientAddresses;
    }
    public List<MimeBodyPart> getMessageParts() {
        return messageParts;
    }
}
