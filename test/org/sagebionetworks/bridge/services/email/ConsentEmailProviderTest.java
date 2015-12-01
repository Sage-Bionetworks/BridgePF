package org.sagebionetworks.bridge.services.email;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.FileInputStream;

import javax.mail.internet.MimeBodyPart;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.dynamodb.DynamoStudyConsent1;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsentView;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.StudyConsentService;

import com.google.common.collect.Sets;

public class ConsentEmailProviderTest {

    private static final long UNIX_TIMESTAMP = DateUtils.getCurrentMillisFromEpoch();
    
    private static final String LEGACY_DOCUMENT = "<html><head></head><body>Passed through as is. |@@name@@|@@signing.date@@|@@email@@|@@sharing@@|</body></html>";
    
    private static final String NEW_DOCUMENT_FRAGMENT = "<p>This is a consent agreement body</p>";
    
    private ConsentEmailProvider provider;
    
    private StudyConsentService studyConsentService;
    
    @Before
    public void before() throws Exception {
        String consentBodyTemplate = IOUtils.toString(new FileInputStream("conf/study-defaults/consent-page.xhtml")); // "<html xmlns='http://www.w3.org/1999/xhtml'><head><title>${studyName} Consent To Research</title></head><body>${consent.body}${consent.signature}</body></html>";
        
        Study study = new DynamoStudy();
        study.setName("Study Name");
        study.setSponsorName("Sponsor Name");
        study.setSupportEmail("sender@default.com");
        study.setConsentNotificationEmail("consent@consent.com");
        
        User user = new User();
        user.setEmail("user@user.com");
        
        ConsentSignature sig = new ConsentSignature.Builder().withName("Test Person").withBirthdate("1980-06-06")
                .withSignedOn(UNIX_TIMESTAMP).build();
        studyConsentService = mock(StudyConsentService.class);
        
        provider = new ConsentEmailProvider(study, user, sig, SharingScope.NO_SHARING, 
            studyConsentService, consentBodyTemplate);
    }
    
    @Test
    public void assemblesOriginalDocumentTypeToEmail() throws Exception {
        StudyConsentView view = new StudyConsentView(new DynamoStudyConsent1(), LEGACY_DOCUMENT);
        when(studyConsentService.getActiveConsent(any(StudyIdentifier.class))).thenReturn(view);
        
        MimeTypeEmail email = provider.getMimeTypeEmail();
        MimeBodyPart body = email.getMessageParts().get(0);
        assertEquals("Consent Agreement for Study Name", email.getSubject());
        assertEquals("\"Study Name\" <sender@default.com>", email.getSenderAddress());
        assertEquals(Sets.newHashSet("user@user.com","consent@consent.com"),
                     Sets.newHashSet(email.getRecipientAddresses()));
        
        assertTrue("Name correct", ((String)body.getContent()).contains("|Test Person|"));
        assertTrue("User email correct", ((String)body.getContent()).contains("|user@user.com|"));
        assertTrue("Sharing correct", ((String)body.getContent()).contains("|Not Sharing|"));
        assertTrue("HTML markup preserved", ((String)body.getContent()).contains("<html><head></head><body>Passed through as is."));
    }
    
    @Test
    public void assemblesNewDocumentTypeToEmail() throws Exception {
        StudyConsentView view = new StudyConsentView(new DynamoStudyConsent1(), NEW_DOCUMENT_FRAGMENT);
        when(studyConsentService.getActiveConsent(any(StudyIdentifier.class))).thenReturn(view);
        
        MimeTypeEmail email = provider.getMimeTypeEmail();
        MimeBodyPart body = email.getMessageParts().get(0);
        assertEquals("Consent Agreement for Study Name", email.getSubject());
        assertEquals("\"Study Name\" <sender@default.com>", email.getSenderAddress());
        assertEquals("user@user.com", email.getRecipientAddresses().get(0));
        assertEquals("consent@consent.com", email.getRecipientAddresses().get(1));
        
        assertTrue("Study name correct", ((String)body.getContent()).contains("<title>Study Name Consent To Research</title>"));
        assertTrue("Name correct", ((String)body.getContent()).contains(">Test Person<"));
        assertTrue("User email correct", ((String)body.getContent()).contains(">user@user.com<"));
        assertTrue("Sharing correct", ((String)body.getContent()).contains(">Not Sharing<"));
    }
    
}
