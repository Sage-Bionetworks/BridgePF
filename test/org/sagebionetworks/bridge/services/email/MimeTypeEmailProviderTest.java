package org.sagebionetworks.bridge.services.email;

import static org.junit.Assert.assertEquals;

import javax.mail.MessagingException;

import org.junit.Test;

import org.sagebionetworks.bridge.models.studies.Study;

public class MimeTypeEmailProviderTest {
    static class MimeTypeEmailProviderImpl extends MimeTypeEmailProvider {
        public MimeTypeEmailProviderImpl(Study study) {
            super(study);
        }
        public MimeTypeEmail getMimeTypeEmail() throws MessagingException {
            return null;
        }
    }
    
    @Test
    public void works() {
        Study study = Study.create();
        study.setName("Very Useful Study 🐶");
        study.setSupportEmail("support@support.com");
        
        MimeTypeEmailProvider provider = new MimeTypeEmailProviderImpl(study);
        
        assertEquals("support@support.com", provider.getPlainSenderEmail());
        assertEquals("Very Useful Study 🐶 <support@support.com>", provider.getFormattedSenderEmail());
    }
    
    @Test
    public void worksWithMultipleAddresses() {
        Study study = Study.create();
        study.setName("Very Useful Study 🐶");
        study.setSupportEmail("support@support.com,email@email.com");
        
        MimeTypeEmailProvider provider = new MimeTypeEmailProviderImpl(study);
        
        assertEquals("support@support.com", provider.getPlainSenderEmail());
        assertEquals("Very Useful Study 🐶 <support@support.com>", provider.getFormattedSenderEmail());
    }
}
