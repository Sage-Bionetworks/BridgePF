package org.sagebionetworks.bridge.services.email;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import javax.mail.internet.MimeBodyPart;

import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.SimpleAccount;

public class WithdrawConsentEmailProviderTest {

    private static final long UNIX_TIMESTAMP = 1446073435148L;
    private static final Withdrawal WITHDRAWAL = new Withdrawal("<p>Because, reasons.</p>");
    private static final String EXTERNAL_ID = "<u>AAA</u>";
    
    private WithdrawConsentEmailProvider provider;
    private Study study;
    private Account account;
    
    @Before
    public void before() {
        study = mock(Study.class);
        
        account = new SimpleAccount();
        account.setEmail("d@d.com");
        
        provider = new WithdrawConsentEmailProvider(study, EXTERNAL_ID, account, WITHDRAWAL, UNIX_TIMESTAMP);
    }
    
    @Test
    public void canGenerateMinimalEmail() throws Exception {
        when(study.getConsentNotificationEmail()).thenReturn("a@a.com");
        when(study.getName()).thenReturn("Study Name");
        when(study.getSupportEmail()).thenReturn("c@c.com");

        provider = new WithdrawConsentEmailProvider(study, null, account, new Withdrawal(null), UNIX_TIMESTAMP);
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
        when(study.getConsentNotificationEmail()).thenReturn("a@a.com, b@b.com");
        when(study.getName()).thenReturn("Study Name");
        when(study.getSupportEmail()).thenReturn("c@c.com");
        account.setFirstName("<b>Jack</b>");
        account.setLastName("<i>Aubrey</i>");

        provider = new WithdrawConsentEmailProvider(study, EXTERNAL_ID, account, WITHDRAWAL, UNIX_TIMESTAMP);
        MimeTypeEmail email = provider.getMimeTypeEmail();
        
        List<String> recipients = email.getRecipientAddresses();
        assertEquals(2, recipients.size());
        assertEquals("a@a.com", recipients.get(0));
        assertEquals("b@b.com", recipients.get(1));
        
        String sender = email.getSenderAddress();
        assertEquals("\"Study Name\" <c@c.com>", sender);
        
        assertEquals("Notification of consent withdrawal for Study Name", email.getSubject());
        
        MimeBodyPart body = email.getMessageParts().get(0);
        assertEquals("<p>User Jack Aubrey &lt;d@d.com&gt; (external ID: AAA)  withdrew from the study on October 28, 2015. </p><p>Reason:</p><p>Because, reasons.</p>", (String)body.getContent());
    }
    
}
