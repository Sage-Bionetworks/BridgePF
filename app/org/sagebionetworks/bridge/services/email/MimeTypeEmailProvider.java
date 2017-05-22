package org.sagebionetworks.bridge.services.email;

import javax.mail.MessagingException;

public interface MimeTypeEmailProvider {

    String getPlainSenderEmail();
    
    MimeTypeEmail getMimeTypeEmail() throws MessagingException;
    
}
