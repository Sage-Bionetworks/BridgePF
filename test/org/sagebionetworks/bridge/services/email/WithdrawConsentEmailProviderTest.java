package org.sagebionetworks.bridge.services.email;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import javax.mail.internet.MimeBodyPart;

import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.Study;

public class WithdrawConsentEmailProviderTest {

    private static final long UNIX_TIMESTAMP = 1446073435148L;
    private static final Withdrawal WITHDRAWAL = new Withdrawal("<p>Because, reasons.</p>");
    
    private WithdrawConsentEmailProvider provider;
    private Study study;
    private Account account;
    
    @Before
    public void before() {
        study = mock(Study.class);
        
        account = Account.create();
        account.setEmail("d@d.com");
        
        provider = new WithdrawConsentEmailProvider(study, account, WITHDRAWAL, UNIX_TIMESTAMP);
    }
    
    @Test
    public void canGenerateMinimalEmail() throws Exception {
        when(study.getConsentNotificationEmail()).thenReturn("a@a.com");
        when(study.isConsentNotificationEmailVerified()).thenReturn(true);
        when(study.getName()).thenReturn("Study Name");
        when(study.getSupportEmail()).thenReturn("c@c.com");

        provider = new WithdrawConsentEmailProvider(study, account, new Withdrawal(null), UNIX_TIMESTAMP);
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals(EmailType.WITHDRAW_CONSENT, email.getType());
        
        List<String> recipients = email.getRecipientAddresses();
        assertEquals(1, recipients.size());
        assertEquals("a@a.com", recipients.get(0));
        
        String sender = email.getSenderAddress();
        assertEquals("\"Study Name\" <c@c.com>", sender);
        
        assertEquals("Notification of consent withdrawal for Study Name", email.getSubject());
        
        MimeBodyPart body = email.getMessageParts().get(0);
        assertEquals("<p>User   &lt;d@d.com&gt; withdrew from the study on October 28, 2015. </p><p>Reason:</p><p><i>No reason given.</i></p>", body.getContent());
    }

    @Test
    public void canGenerateMaximalEmail() throws Exception {
        when(study.getConsentNotificationEmail()).thenReturn("a@a.com, b@b.com");
        when(study.isConsentNotificationEmailVerified()).thenReturn(true);
        when(study.getName()).thenReturn("Study Name");
        when(study.getSupportEmail()).thenReturn("c@c.com");
        account.setFirstName("<b>Jack</b>");
        account.setLastName("<i>Aubrey</i>");

        provider = new WithdrawConsentEmailProvider(study, account, WITHDRAWAL, UNIX_TIMESTAMP);
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals(EmailType.WITHDRAW_CONSENT, email.getType());

        List<String> recipients = email.getRecipientAddresses();
        assertEquals(2, recipients.size());
        assertEquals("a@a.com", recipients.get(0));
        assertEquals("b@b.com", recipients.get(1));
        
        String sender = email.getSenderAddress();
        assertEquals("\"Study Name\" <c@c.com>", sender);
        
        assertEquals("Notification of consent withdrawal for Study Name", email.getSubject());
        
        MimeBodyPart body = email.getMessageParts().get(0);
        assertEquals("<p>User Jack Aubrey &lt;d@d.com&gt; withdrew from the study on October 28, 2015. </p><p>Reason:</p><p>Because, reasons.</p>", body.getContent());
    }
    
    @Test
    public void unverifiedStudyConsentEmailGeneratesNoRecipients() {
        study.setConsentNotificationEmailVerified(false);
        provider = new WithdrawConsentEmailProvider(study, account, WITHDRAWAL, UNIX_TIMESTAMP);
        
        assertTrue(provider.getRecipients().isEmpty());
    }
    
    @Test
    public void nullStudyConsentEmailGeneratesNoRecipients() {
        // email shouldn't be verified if it is null, but regardless, there should still be no recipients
        study.setConsentNotificationEmailVerified(true); 
        study.setConsentNotificationEmail(null);
        provider = new WithdrawConsentEmailProvider(study, account, WITHDRAWAL, UNIX_TIMESTAMP);
        
        assertTrue(provider.getRecipients().isEmpty());
    }
}
