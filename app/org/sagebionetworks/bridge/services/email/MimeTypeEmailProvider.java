package org.sagebionetworks.bridge.services.email;

import javax.mail.MessagingException;

public interface MimeTypeEmailProvider {

    /**
     * Get the sender email address without any further formatting. So for example, if the provider formats the sender
     * email as <code>"Study Name" &lt;email@email.com&gtl</code>, This method should return only
     * <code>email@email.com</code>.
     */
    String getPlainSenderEmail();
    
    MimeTypeEmail getMimeTypeEmail() throws MessagingException;
    
}
