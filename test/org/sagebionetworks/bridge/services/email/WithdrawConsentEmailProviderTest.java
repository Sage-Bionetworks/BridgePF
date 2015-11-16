package org.sagebionetworks.bridge.services.email;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import javax.mail.internet.MimeBodyPart;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.Study;

public class WithdrawConsentEmailProviderTest {

    private static long UNIX_TIMESTAMP = 1446073435148L;
    private static Withdrawal WITHDRAWAL = new Withdrawal("Because, reasons.");
    private static String EXTERNAL_ID = "AAA";
    
    private WithdrawConsentEmailProvider provider;
    private Study study;
    private User user;
    
    @Before
    public void before() {
        study = mock(Study.class);
        user = mock(User.class);
        provider = new WithdrawConsentEmailProvider(study, EXTERNAL_ID, user, WITHDRAWAL, UNIX_TIMESTAMP);
    }
    
    @Test
    public void canGenerateMinimalEmail() throws Exception {
        provider = new WithdrawConsentEmailProvider(study, null, user, new Withdrawal(null), UNIX_TIMESTAMP);
        
        when(study.getConsentNotificationEmail()).thenReturn("a@a.com");
        when(study.getName()).thenReturn("Study Name");
        when(study.getSupportEmail()).thenReturn("c@c.com");
        when(user.getEmail()).thenReturn("d@d.com");

        MimeTypeEmail email = provider.getMimeTypeEmail();
        
        List<String> recipients = email.getRecipientAddresses();
        assertEquals(1, recipients.size());
        assertEquals("a@a.com", recipients.get(0));
        
        String sender = email.getSenderAddress();
        assertEquals("\"Study Name\" <c@c.com>", sender);
        
        assertEquals("Notification of consent withdrawal for Study Name", email.getSubject());
        
        MimeBodyPart body = email.getMessageParts().get(0);
        assertEquals("<p>User   &lt;d@d.com&gt; withdrew from the study on October 28, 2015. </p><p>Reason:</p><p><i>No reason given.</i></p>", (String)body.getContent());
    }

    @Test
    public void canGenerateMaximalEmail() throws Exception {
        provider = new WithdrawConsentEmailProvider(study, EXTERNAL_ID, user, WITHDRAWAL, UNIX_TIMESTAMP);
        
        when(study.getConsentNotificationEmail()).thenReturn("a@a.com, b@b.com");
        when(study.getName()).thenReturn("Study Name");
        when(study.getSupportEmail()).thenReturn("c@c.com");
        when(user.getFirstName()).thenReturn("Jack");
        when(user.getLastName()).thenReturn("Aubrey");
        when(user.getEmail()).thenReturn("d@d.com");
        
        MimeTypeEmail email = provider.getMimeTypeEmail();
        
        List<String> recipients = email.getRecipientAddresses();
        assertEquals(2, recipients.size());
        assertEquals("a@a.com", recipients.get(1));
        assertEquals("b@b.com", recipients.get(0));
        
        String sender = email.getSenderAddress();
        assertEquals("\"Study Name\" <c@c.com>", sender);
        
        assertEquals("Notification of consent withdrawal for Study Name", email.getSubject());
        
        MimeBodyPart body = email.getMessageParts().get(0);
        assertEquals("<p>User Jack Aubrey &lt;d@d.com&gt; (external ID: AAA)  withdrew from the study on October 28, 2015. </p><p>Reason:</p><p>Because, reasons.</p>", (String)body.getContent());
    }
    
}
