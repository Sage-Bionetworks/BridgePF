package org.sagebionetworks.bridge.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Properties;

import javax.annotation.Resource;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.services.email.MimeTypeEmail;
import org.sagebionetworks.bridge.services.email.MimeTypeEmailProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.RawMessage;
import com.amazonaws.services.simpleemail.model.SendRawEmailRequest;
import com.amazonaws.services.simpleemail.model.SendRawEmailResult;
import com.google.common.base.Charsets;

@Component("sendEmailViaAmazonService")
public class SendMailViaAmazonService implements SendMailService {

    private static final Logger logger = LoggerFactory.getLogger(SendMailViaAmazonService.class);

    private static final Region REGION = Region.getRegion(Regions.US_EAST_1);

    private String supportEmail;
    private AmazonSimpleEmailServiceClient emailClient;

    @Resource(name="supportEmail")
    public void setSupportEmail(String supportEmail) {
        this.supportEmail = supportEmail;
    }
    @Autowired
    public void setEmailClient(AmazonSimpleEmailServiceClient emailClient) {
        this.emailClient = emailClient;
    }
    
    @Override
    public void sendEmail(MimeTypeEmailProvider provider) {
        try {
            MimeTypeEmail email = provider.getEmail(supportEmail);
            for (String recipient: email.getRecipientAddresses()) {
                sendEmail(recipient, email);    
            }
        } catch(MessagingException | AmazonServiceException | IOException e) {
            throw new BridgeServiceException(e);
        }
    }

    private void sendEmail(String recipient, MimeTypeEmail email) throws AmazonClientException, MessagingException, IOException {
        Session mailSession = Session.getInstance(new Properties(), null);
        MimeMessage mimeMessage = new MimeMessage(mailSession);
        mimeMessage.setFrom(new InternetAddress(email.getSenderAddress()));
        mimeMessage.setSubject(email.getSubject(), Charsets.UTF_8.name());
        mimeMessage.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));

        MimeMultipart mimeMultipart = new MimeMultipart();
        for (MimeBodyPart part : email.getMessageParts()) {
            if (part != null) {
                mimeMultipart.addBodyPart(part);    
            }
        }

        // Convert MimeMessage to raw text to send to SES.
        mimeMessage.setContent(mimeMultipart);
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        mimeMessage.writeTo(byteOutputStream);
        RawMessage sesRawMessage = new RawMessage(ByteBuffer.wrap(byteOutputStream.toByteArray()));

        SendRawEmailRequest req = new SendRawEmailRequest(sesRawMessage);
        req.setSource(email.getSenderAddress());
        req.setDestinations(Collections.singleton(recipient));
        emailClient.setRegion(REGION);
        SendRawEmailResult result = emailClient.sendRawEmail(req);

        logger.info(String.format("Sent email to SES with message ID %s", result.getMessageId()));
    }
    
}
