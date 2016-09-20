package org.sagebionetworks.bridge.services.email;

import javax.mail.MessagingException;

public interface MimeTypeEmailProvider {

    MimeTypeEmail getMimeTypeEmail() throws MessagingException;
    
}
