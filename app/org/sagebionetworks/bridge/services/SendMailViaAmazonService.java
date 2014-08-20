package org.sagebionetworks.bridge.services;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.ResearchConsent;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;
import com.google.common.io.CharStreams;

public class SendMailViaAmazonService implements SendMailService {

    private static Region region = Region.getRegion(Regions.US_EAST_1);
    
    private String fromEmail;
    private AmazonSimpleEmailServiceClient emailClient;
    
    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }
    
    public void setEmailClient(AmazonSimpleEmailServiceClient emailClient) {
        this.emailClient = emailClient;
    }
    
    @Override
    public void sendConsentAgreement(User user, ResearchConsent consent, Study study) {
        try {
            Content subject = new Content().withData("Consent Agreement for " + study.getName());
            Body body = createSignedDocument(consent, study);
            Message message = new Message().withSubject(subject).withBody(body);
            
            Destination destination = new Destination().withToAddresses(new String[]{user.getEmail()});
            SendEmailRequest request = new SendEmailRequest(fromEmail, destination, message);
            
            emailClient.setRegion(region);
            emailClient.sendEmail(request);
        } catch (Throwable t) {
            throw new BridgeServiceException(t, 500);
        }
    }
    
    private Body createSignedDocument(ResearchConsent consent, Study study) throws UnsupportedEncodingException, IOException {
        InputStreamReader isr = new InputStreamReader(study.getConsentAgreement().getInputStream(), "UTF-8");
        String consentAgreementHTML = CharStreams.toString(isr);

        LocalDate date = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormat.forPattern("MMMM d, yyyy");
        String signingDate = date.toString(fmt);
        
        DateTimeFormatter fmt2 = DateTimeFormat.forPattern("yyyy-mm-dd");
        DateTime birthdate = fmt2.parseDateTime(consent.getBirthdate());

        String html = consentAgreementHTML.replace("@@name@@", consent.getName());
        html = html.replace("@@birth.date@@", consent.getBirthdate());
        html = html.replace("@@signing.date@@", signingDate);
        Content textBody = new Content().withData(html); 
        return new Body().withHtml(textBody);
    }

}
