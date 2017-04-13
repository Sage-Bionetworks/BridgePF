package org.sagebionetworks.bridge.services.email;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.studies.Study;

public class EmailSignInEmailProviderTest {

    private static final String RECIPIENT_EMAIL = "recipient@recipient.com";

    @Test
    public void testProvider() throws Exception {
        Study study = new DynamoStudy();
        study.setName("Study name");
        study.setSupportEmail("support@email.com");
        
        // Verifying in particular that all instances of a template variable are replaced
        // in the template.
        EmailSignInEmailProvider provider = new EmailSignInEmailProvider(study, RECIPIENT_EMAIL,
                "${studyName} sign in link",
                "Click here to sign in: <a href=\"https://${host}/mobile/startSession.html?token=${token}\">https://${host}/mobile/startSession.html?token=${token}</a>", "ABC");
        
        String url = "https://"
                + BridgeConfigFactory.getConfig().getHostnameWithPostfix("webservices")
                + "/mobile/startSession.html?token=ABC";
        
        String finalBody = "Click here to sign in: <a href=\""+url+"\">"+url+"</a>";
        
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals("\"Study name\" <support@email.com>", email.getSenderAddress());
        assertEquals(RECIPIENT_EMAIL, email.getRecipientAddresses().get(0));
        assertEquals("Study name sign in link", email.getSubject());
        assertEquals(finalBody, email.getMessageParts().get(0).getContent());
    }
    
}
