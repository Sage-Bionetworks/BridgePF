package org.sagebionetworks.bridge.services.email;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;
import java.util.function.Function;
import javax.mail.internet.MimeBodyPart;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * This is a light wrapper around the information needed to construct a MIME multi-part email 
 * message using the Java mail API.
 */
public final class MimeTypeEmail {
    private static final String QUOTE = "\"";
    private static final String QUOTED_QUOTE = "\\\\\"";
    private static final Function<String, String> APPLY_EMAIL_ESCAPER = MimeTypeEmail::escapeEmailAddress;

    private final String subject;
    private final String senderAddress;
    private final List<String> recipientAddresses;
    private final List<MimeBodyPart> messageParts;
    private final EmailType type;
    
    MimeTypeEmail(String subject, String senderAddress, List<String> recipientAddresses,
            List<MimeBodyPart> messageParts, EmailType type) {
        checkArgument(isNotBlank(subject));
        checkNotNull(recipientAddresses);
        checkArgument(messageParts != null && !messageParts.isEmpty());
        checkNotNull(type);
        
        this.subject = subject;
        this.senderAddress = (senderAddress != null) ? escapeEmailAddress(senderAddress) : null;
        this.recipientAddresses = Lists.transform(recipientAddresses, APPLY_EMAIL_ESCAPER::apply);
        this.messageParts = ImmutableList.copyOf(messageParts);
        this.type = type;
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

    /** Email type. Examples include email verification, sign-in, consent, etc. */
    public EmailType getType() {
        return type;
    }

    /**
     * Addresses can be submitted in forms such as "Study A, B, and C <study@study.com>" and need to be escaped. This 
     * method will do that if necessary.
     */
    private static String escapeEmailAddress(String address) {
        if (address.matches("^.+<.*>$")) {
            String[] parts =  address.split("<");
            String escapedName = parts[0].replaceAll(QUOTE,  QUOTED_QUOTE).trim();
            return QUOTE + escapedName + QUOTE + " <" + parts[1];
        }
        return address;
    }
}
