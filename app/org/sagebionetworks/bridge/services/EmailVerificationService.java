package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Map;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.cache.CacheProvider;

import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.GetIdentityVerificationAttributesRequest;
import com.amazonaws.services.simpleemail.model.GetIdentityVerificationAttributesResult;
import com.amazonaws.services.simpleemail.model.IdentityVerificationAttributes;
import com.amazonaws.services.simpleemail.model.VerifyEmailIdentityRequest;

/**
 * Service to verify an email address can be used to send SES emails. Will be called independently of 
 * the study APIs that retrieve a cached study, because it needs to be cached on a very different time period 
 * from studies (studies can be cached forever, while this status updates outside the system and should be 
 * held for much shorter time period).
 */
@Component
public class EmailVerificationService {

    private static final Logger LOG = LoggerFactory.getLogger(EmailVerificationService.class);
    
    private static final int VERIFIED_EMAIL_CACHE_IN_SECONDS = 60*5;
    
    private static final String KEY_POSTFIX = ":emailVerificationStatus";
    
    private AmazonSimpleEmailServiceClient sesClient;
    private CacheProvider cacheProvider;

    @Resource(name="sesClient")
    final void setAmazonSimpleEmailServiceClient(AmazonSimpleEmailServiceClient sesClient) {
        this.sesClient = sesClient;
    }
    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }
    
    private String getVerifiedAddressKey(String emailAddress) {
        return emailAddress + KEY_POSTFIX;
    }
    
    private EmailVerificationStatus cacheAndReturn(String emailAddress, EmailVerificationStatus status) {
        String key = getVerifiedAddressKey(emailAddress);
        cacheProvider.setObject(key, status.name(), VERIFIED_EMAIL_CACHE_IN_SECONDS);
        return status;
    }
    
    public boolean isVerified(String emailAddress) {
        String key = getVerifiedAddressKey(emailAddress);
        String value = cacheProvider.getObject(key, String.class);
        if (value == null) {
            EmailVerificationStatus status = getEmailStatus(emailAddress);
            value = cacheAndReturn(emailAddress, status).name();
        }
        return "VERIFIED".equals(value);
    }
    
    public EmailVerificationStatus getEmailStatus(String emailAddress) {
        checkArgument(isNotBlank(emailAddress));
        
        GetIdentityVerificationAttributesRequest request = new GetIdentityVerificationAttributesRequest()
                .withIdentities(emailAddress);

        GetIdentityVerificationAttributesResult result = sesClient.getIdentityVerificationAttributes(request);
        
        // didn't happen in testing against SES, but just to be paranoid.
        Map<String,IdentityVerificationAttributes> attributeMap = result.getVerificationAttributes();
        if (attributeMap == null) {
            return cacheAndReturn(emailAddress, EmailVerificationStatus.UNVERIFIED);
        }
        IdentityVerificationAttributes attributes = attributeMap.get(emailAddress);
        if (attributes == null || attributes.getVerificationStatus() == null) {
            return cacheAndReturn(emailAddress, EmailVerificationStatus.UNVERIFIED);
        }
        String statusString = attributes.getVerificationStatus();

        return cacheAndReturn(emailAddress, EmailVerificationStatus.fromSesVerificationStatus(statusString));
    }
    
    /**
     * Get the status of this email address. If the address is unknown to SES or it in the state UNVERIFIED, then 
     * we will have SES send out a request to verify the email address and return the status PENDING. Otherwise, 
     * we return the status (in this case, it should be VERIFIED). This is the primary method called when getting, 
     * saving or updating a study.
     * 
     * @param emailAddress
     * @return emailVerificationStatus
     */
    public EmailVerificationStatus verifyEmailAddress(String emailAddress) {
        checkArgument(isNotBlank(emailAddress));
        
        EmailVerificationStatus status = getEmailStatus(emailAddress);
        if (status == EmailVerificationStatus.UNVERIFIED) {
            LOG.info("status is unverified for "+emailAddress);
            return sendVerifyEmailRequest(emailAddress);
        }
        return status;
    }

    /**
     * Verify the email identity. We always do this when we cannot find the email address at SES. This method can 
     * be called directly to re-send the verification request, even if the email address is already pending.
     * 
     * @param emailAddress
     * @return emailVerificationStatus
     */
    public EmailVerificationStatus sendVerifyEmailRequest(String emailAddress) {
        checkArgument(isNotBlank(emailAddress));
        
        LOG.info("sending email verification email for "+emailAddress);
        VerifyEmailIdentityRequest request = new VerifyEmailIdentityRequest().withEmailAddress(emailAddress);
        sesClient.verifyEmailIdentity(request);
        return cacheAndReturn(emailAddress, EmailVerificationStatus.PENDING);
    }
}
