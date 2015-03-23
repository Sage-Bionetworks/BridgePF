package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.services.email.MimeTypeEmailProvider;

public interface SendMailService {

    public void sendEmail(MimeTypeEmailProvider provider);

}
