package org.sagebionetworks.bridge.services.email;

import javax.mail.MessagingException;

public interface MimeTypeEmailProvider {

    public MimeTypeEmail getEmail(String defaultFromEmail) throws MessagingException;
    
}
