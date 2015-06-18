package org.sagebionetworks.bridge.services.email;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import javax.mail.internet.MimeBodyPart;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.dynamodb.DynamoStudyConsent1;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsentView;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.StudyConsentService;

public class ConsentEmailProviderTest {

    private static final String LEGACY_DOCUMENT = "<html><head></head><body>Passed through as is.</body></html>";
    
    private static final String NEW_DOCUMENT_FRAGMENT = "<p>This is a consent agreement body.</p>";
    
    private ConsentEmailProvider provider;
    
    private StudyConsentService studyConsentService;
    
    @Before
    public void before() {
        String consentBodyTemplate = "<html xmlns='http://www.w3.org/1999/xhtml'><head><title>${studyName} Consent To Research</title></head><body>${consent.body}${consent.signature}</body></html>";
        String consentSignatureBlockTemplate = "<table><tr><td>${participant.name}</td><td><img alt='' src='cid:consentSignature' /></td><td>${participant.signing.date}</td></tr><tr><td>${participant.email}</td><td>${participant.sharing}</td><td></td></tr></table>";
        
        Study study = new DynamoStudy();
        study.setName("Study Name");
        study.setSponsorName("Sponsor Name");
        study.setConsentNotificationEmail("consent@consent.com");
        
        User user = new User();
        user.setEmail("user@user.com");
        
        ConsentSignature sig = ConsentSignature.create("Test Person", "1980-06-06", null, null);
        studyConsentService = mock(StudyConsentService.class);
        
        provider = new ConsentEmailProvider(study, user, sig, SharingScope.NO_SHARING, 
            studyConsentService, consentBodyTemplate, consentSignatureBlockTemplate);
    }
    
    @Test
    public void assemblesOriginalDocumentTypeToEmail() throws Exception {
        StudyConsentView view = new StudyConsentView(new DynamoStudyConsent1(), LEGACY_DOCUMENT);
        when(studyConsentService.getActiveConsent(any(StudyIdentifier.class))).thenReturn(view);
        
        MimeTypeEmail email = provider.getEmail("sender@default.com");
        MimeBodyPart body = email.getMessageParts().get(0);
        assertEquals("Consent Agreement for Study Name", email.getSubject());
        assertEquals("sender@default.com", email.getSenderAddress());
        assertEquals("user@user.com", email.getRecipientAddresses().get(0));
        assertEquals("consent@consent.com", email.getRecipientAddresses().get(1));
        assertEquals(LEGACY_DOCUMENT, body.getContent());
    }
    
    @Test
    public void assemblesNewDocumentTypeToEmail() throws Exception {
        StudyConsentView view = new StudyConsentView(new DynamoStudyConsent1(), NEW_DOCUMENT_FRAGMENT);
        when(studyConsentService.getActiveConsent(any(StudyIdentifier.class))).thenReturn(view);
        
        MimeTypeEmail email = provider.getEmail("sender@default.com");
        MimeBodyPart body = email.getMessageParts().get(0);
        assertEquals("Consent Agreement for Study Name", email.getSubject());
        assertEquals("sender@default.com", email.getSenderAddress());
        assertEquals("user@user.com", email.getRecipientAddresses().get(0));
        assertEquals("consent@consent.com", email.getRecipientAddresses().get(1));
        
        assertTrue("Study name correct", ((String)body.getContent()).contains("<title>Study Name Consent To Research</title>"));
        assertTrue("Name correct", ((String)body.getContent()).contains("<td>Test Person</td>"));
        assertTrue("User email correct", ((String)body.getContent()).contains("<td>user@user.com</td>"));
        assertTrue("Shairng correct", ((String)body.getContent()).contains("<td>Not Sharing</td>"));
    }
    
}
