package org.sagebionetworks.bridge.services;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.ConsentSignatureImage;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.springframework.core.io.FileSystemResource;

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

    private static final DateTimeFormatter fmt = DateTimeFormat.forPattern("MMMM d, yyyy");
    private static final Region region = Region.getRegion(Regions.US_EAST_1);

    private String fromEmail;
    private AmazonSimpleEmailServiceClient emailClient;
    private StudyService studyService;

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public void setEmailClient(AmazonSimpleEmailServiceClient emailClient) {
        this.emailClient = emailClient;
    }

    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    @Override
    public void sendConsentAgreement(User user, ConsentSignature consentSignature, StudyConsent studyConsent) {
        try {
            Study study = studyService.getStudyByIdentifier(studyConsent.getStudyKey());
            Content subject = new Content().withData("Consent Agreement for " + study.getName());
            Body body = createSignedDocument(consentSignature, studyConsent);
            Message message = new Message().withSubject(subject).withBody(body);
            Destination destination = new Destination().withToAddresses(new String[]{user.getEmail()});
            SendEmailRequest request = new SendEmailRequest(fromEmail, destination, message);
            emailClient.setRegion(region);
            emailClient.sendEmail(request);
        } catch (Throwable t) {
            throw new BridgeServiceException(t);
        }
    }

    private Body createSignedDocument(ConsentSignature consent, StudyConsent studyConsent) throws UnsupportedEncodingException, IOException {

        String filePath = studyConsent.getPath();
        FileSystemResource resource = new FileSystemResource(filePath);
        InputStreamReader isr = new InputStreamReader(resource.getInputStream(), "UTF-8");
        String consentAgreementHTML = CharStreams.toString(isr);

        String signingDate = fmt.print(DateUtils.getCurrentMillisFromEpoch());
        // The dates we're showing are internally stored as UTC dates, so convert to 
        // LocalDate which will show the date the user entered.
        LocalDate localDate = LocalDate.parse(consent.getBirthdate());
        String birthdate = fmt.print(localDate);

        String html = consentAgreementHTML.replace("@@name@@", consent.getName());
        html = html.replace("@@birth.date@@", birthdate);
        html = html.replace("@@signing.date@@", signingDate);

        // Signature image, if we have it.
        ConsentSignatureImage sigImg = consent.getImage();
        if (sigImg != null) {
            html = html.replace("@@signature.image.mime.type@@", sigImg.getMimeType());
            html = html.replace("@@signature.image.data@@", sigImg.getData());
        }

        Content textBody = new Content().withData(html); 
        return new Body().withHtml(textBody);
    }
}
