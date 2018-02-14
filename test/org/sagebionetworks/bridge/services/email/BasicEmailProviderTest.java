package org.sagebionetworks.bridge.services.email;

import static org.junit.Assert.assertEquals;

import javax.mail.internet.MimeBodyPart;

import org.junit.Test;

import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.Study;

import com.google.common.collect.Sets;

public class BasicEmailProviderTest {
    @Test
    public void test() throws Exception {
        // Set up dependencies
        Study study = Study.create();
        study.setName("Study Name");
        study.setSupportEmail("email@email.com,email2@email.com");
        
        EmailTemplate template = new EmailTemplate("Subject ${url}", "Body ${url}", MimeType.HTML);

        // Create
        BasicEmailProvider provider = new BasicEmailProvider.Builder()
            .withStudy(study)
            .withRecipientEmail("recipient@recipient.com")
            .withRecipientEmail("recipient2@recipient.com")
            .withEmailTemplate(template)
            .withToken("url", "some-url").build();

        // Check provider attributes
        assertEquals("Study Name <email@email.com>", provider.getFormattedSenderEmail());

        // Check email
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals("Subject some-url", email.getSubject());
        assertEquals("\"Study Name\" <email@email.com>", email.getSenderAddress());
        assertEquals(Sets.newHashSet("recipient@recipient.com", "recipient2@recipient.com"),
                Sets.newHashSet(email.getRecipientAddresses()));
        MimeBodyPart body = email.getMessageParts().get(0);
        
        String bodyString = (String)body.getContent();
        assertEquals("Body some-url", bodyString);
    }

    @Test
    public void withOverrideSenderEmail() {
        // Set up dependencies
        Study study = Study.create();
        study.setName("Study Name");
        study.setSupportEmail("email@email.com");

        EmailTemplate template = new EmailTemplate("Subject ${url}", "Body ${url}", MimeType.HTML);

        // Create
        BasicEmailProvider provider = new BasicEmailProvider.Builder().withEmailTemplate(template)
                .withOverrideSenderEmail("example@example.com").withStudy(study).build();

        // Check provider attributes
        assertEquals("example@example.com", provider.getPlainSenderEmail());
    }
}
