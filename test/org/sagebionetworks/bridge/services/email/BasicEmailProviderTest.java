package org.sagebionetworks.bridge.services.email;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.InputStream;
import java.util.Map;

import javax.mail.Part;
import javax.mail.internet.MimeBodyPart;

import org.apache.commons.io.IOUtils;
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
        study.setName("Name");
        study.setShortName("ShortName");
        study.setIdentifier("id");
        study.setSponsorName("SponsorName");
        study.setSupportEmail("support@email.com");
        study.setTechnicalEmail("tech@email.com");
        study.setConsentNotificationEmail("consent@email.com,consent2@email.com");

        EmailTemplate template = new EmailTemplate("Subject ${url}", 
            "${studyName} ${studyShortName} ${studyId} ${sponsorName} ${supportEmail} "+
            "${technicalEmail} ${consentEmail} ${url} ${expirationPeriod}", MimeType.HTML); 
        
        // Create
        BasicEmailProvider provider = new BasicEmailProvider.Builder()
            .withStudy(study)
            .withRecipientEmail("recipient@recipient.com")
            .withRecipientEmail("recipient2@recipient.com")
            .withEmailTemplate(template)
            .withExpirationPeriod("expirationPeriod", 60*60)
            .withToken("url", "some-url")
            .withType(EmailType.EMAIL_SIGN_IN)
            .build();

        // Check provider attributes
        assertEquals("Name <support@email.com>", provider.getFormattedSenderEmail());
        assertEquals(EmailType.EMAIL_SIGN_IN, provider.getType());

        // Check email
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals("Subject some-url", email.getSubject());
        assertEquals("\"Name\" <support@email.com>", email.getSenderAddress());
        assertEquals(Sets.newHashSet("recipient@recipient.com", "recipient2@recipient.com"),
                Sets.newHashSet(email.getRecipientAddresses()));
        assertEquals(EmailType.EMAIL_SIGN_IN, email.getType());

        MimeBodyPart body = email.getMessageParts().get(0);
        String bodyString = (String)body.getContent();
        assertEquals("Name ShortName id SponsorName support@email.com tech@email.com consent@email.com some-url 1 hour", 
                bodyString);
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
    
    @Test
    public void nullTokenMapEntryDoesntBreakMap() throws Exception {
        EmailTemplate template = new EmailTemplate("asdf", "asdf", MimeType.TEXT);
        
        BasicEmailProvider provider = new BasicEmailProvider.Builder().withEmailTemplate(template)
                .withRecipientEmail("email@email.com")
                .withOverrideSenderEmail("example@example.com").withStudy(Study.create()).build();
        
        Map<String,String> tokenMap = provider.getTokenMap();
        assertNull(tokenMap.get("supportName"));
    }    
    
    @Test
    public void canAddBinaryAttachment() throws Exception {
        EmailTemplate template = new EmailTemplate("Subject ${url}", "Body ${url}", MimeType.HTML);
        
        BasicEmailProvider provider = new BasicEmailProvider.Builder()
                .withBinaryAttachment("content.pdf", MimeType.PDF, "some data".getBytes())
                .withRecipientEmail("email@email.com")
                .withOverrideSenderEmail("example@example.com")
                .withEmailTemplate(template)
                .withStudy(Study.create()).build();
        
        MimeTypeEmail email = provider.getMimeTypeEmail();
        
        MimeBodyPart attachment = email.getMessageParts().get(1);
        
        String bodyContent = IOUtils.toString((InputStream)attachment.getContent()); 
        assertEquals("content.pdf", attachment.getFileName());
        assertEquals("some data", bodyContent);
        assertEquals(Part.ATTACHMENT, attachment.getDisposition());
        // the mime type isn't changed because headers are not updated until you call
        // MimeMessage.saveChanges(), and these objects do not include the final 
        // Java Mail MimeMessage object.
    }
}
