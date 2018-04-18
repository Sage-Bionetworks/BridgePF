package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import javax.annotation.Resource;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.simpleemail.model.GetIdentityNotificationAttributesRequest;
import com.amazonaws.services.simpleemail.model.GetIdentityNotificationAttributesResult;
import com.amazonaws.services.simpleemail.model.IdentityNotificationAttributes;
import com.amazonaws.services.simpleemail.model.NotificationType;
import com.amazonaws.services.simpleemail.model.SetIdentityNotificationTopicRequest;
import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.async.AsyncHandler;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;

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

    static final String CONFIG_KEY_NOTIFICATION_TOPIC_ARN = "ses.notification.topic.arn";
    private static final int VERIFIED_EMAIL_CACHE_IN_SECONDS = 60*5;

    // config
    private String notificationTopicArn;

    // dependent services
    private ExecutorService asyncExecutorService;
    private AmazonSimpleEmailServiceClient sesClient;
    private CacheProvider cacheProvider;

    // Most SES administrative calls are throttled to 1 call per second.
    private final RateLimiter sesRateLimiter = RateLimiter.create(1.0);

    // Can be overriden for test.
    private int maxSesTries = 5;

    /** Async thread pool. This is configured by Spring. */
    @Resource(name = "asyncExecutorService")
    public final void setAsyncExecutorService(ExecutorService asyncExecutorService) {
        this.asyncExecutorService = asyncExecutorService;
    }

    /** Sets parameters from the specified Bridge config. */
    @Autowired
    public final void setConfig(BridgeConfig config) {
        notificationTopicArn = config.getProperty(CONFIG_KEY_NOTIFICATION_TOPIC_ARN);
    }

    @Resource(name="sesClient")
    final void setAmazonSimpleEmailServiceClient(AmazonSimpleEmailServiceClient sesClient) {
        this.sesClient = sesClient;
    }

    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }

    /**
     * Retry policy for SES requests. This is the number of times we will try a single request before we fail. Defaults
     * to 5. Made overridable for unit tests.
     */
    public final void setMaxSesTries(int maxSesTries) {
        this.maxSesTries = maxSesTries;
    }

    /**
     * Rate limit for SES requests, in requests per second.. Defaults to 1 request per second. Made overridable for
     * unit tests.
     */
    public final void setSesRateLimit(double rate) {
        sesRateLimiter.setRate(rate);
    }

    private EmailVerificationStatus cacheAndReturn(String emailAddress, EmailVerificationStatus status) {
        CacheKey key = CacheKey.emailVerification(emailAddress);
        cacheProvider.setObject(key, status.name(), VERIFIED_EMAIL_CACHE_IN_SECONDS);
        return status;
    }
    
    public boolean isVerified(String emailAddress) {
        CacheKey key = CacheKey.emailVerification(emailAddress);
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
     * @param emailAddress email address to verify
     * @return emailVerificationStatus
     */
    public EmailVerificationStatus verifyEmailAddress(String emailAddress) {
        checkArgument(isNotBlank(emailAddress));
        
        EmailVerificationStatus status = getEmailStatus(emailAddress);
        if (status == EmailVerificationStatus.UNVERIFIED) {
            // SES administrative requests (verify email, set notification topics) are throttled at a rate of 1 per
            // second. To avoid really slow calls, we kick off an async thread to handle this.
            AsyncSnsTopicHandler handler = new AsyncSnsTopicHandler(emailAddress);
            asyncExecutorService.execute(handler);
            return cacheAndReturn(emailAddress, EmailVerificationStatus.PENDING);
        }
        return status;
    }

    /**
     * Async handler to verify the sender email and set SNS notification topics for bounces and complaints.
     * Package-scoped for unit tests.
     */
    class AsyncSnsTopicHandler extends AsyncHandler {
        private final String emailAddress;

        /** Constructs the async handler with the email address to process. */
        public AsyncSnsTopicHandler(String emailAddress) {
            checkArgument(isNotBlank(emailAddress));
            this.emailAddress = emailAddress;
        }

        /** The email address to be processed. Visible for unit tests. */
        String getEmailAddress() {
            return emailAddress;
        }

        @Override
        protected void handle() {
            // Request sender email verification for SES.
            LOG.info("Sending email verification email for " + emailAddress);
            VerifyEmailIdentityRequest verifyEmailRequest = new VerifyEmailIdentityRequest().withEmailAddress(
                    emailAddress);
            callWrapper(() -> sesClient.verifyEmailIdentity(verifyEmailRequest));

            // Check to see if we have SNS topics configured for bounce and complaints.
            GetIdentityNotificationAttributesRequest getNotificationsRequest =
                    new GetIdentityNotificationAttributesRequest().withIdentities(emailAddress);
            GetIdentityNotificationAttributesResult getNotificationsResult = callWrapper(() -> sesClient
                    .getIdentityNotificationAttributes(getNotificationsRequest));

            String bounceTopic = null;
            String complaintsTopic = null;
            IdentityNotificationAttributes notificationAttributes = getNotificationsResult.getNotificationAttributes()
                    .get(emailAddress);
            if (notificationAttributes != null) {
                bounceTopic = notificationAttributes.getBounceTopic();
                complaintsTopic = notificationAttributes.getComplaintTopic();
            }

            // Note that we have to call set separately for bounce and complaints. There's no API to set both at once.
            if (!notificationTopicArn.equals(bounceTopic)) {
                // Bounce topic is not set, or is set incorrectly. Fix it.
                updateNotificationTopicForType(NotificationType.Bounce);
            }
            if (!notificationTopicArn.equals(complaintsTopic)) {
                // Similarly for complaints.
                updateNotificationTopicForType(NotificationType.Complaint);
            }

            LOG.info("Done sending verification email and setting notification topics for " + emailAddress);
        }

        // Helper method to set the notification topic for the given notification type.
        private void updateNotificationTopicForType(NotificationType notificationType) {
            SetIdentityNotificationTopicRequest setNotificationRequest = new SetIdentityNotificationTopicRequest()
                    .withIdentity(emailAddress).withNotificationType(notificationType)
                    .withSnsTopic(notificationTopicArn);
            callWrapper(() -> sesClient.setIdentityNotificationTopic(setNotificationRequest));
        }

        // Helper method, which wraps retries and rate limiting. Package-scoped for unit tests.
        <T> T callWrapper(Callable<T> callable) {
            Exception lastException = null;
            for (int i = 0; i < maxSesTries; i++) {
                // Need to rate limit at the start of each call.
                sesRateLimiter.acquire();

                try {
                    return callable.call();
                } catch (AmazonServiceException ex) {
                    LOG.warn("Attempt " + i + " of " + maxSesTries + " to verify sender email failed: " +
                            ex.getMessage(), ex);
                    lastException = ex;

                    // If it's a 5XX error, retry.
                    int statusCode = ex.getStatusCode();
                    if (statusCode >= 500 && statusCode <= 599) {
                        continue;
                    }

                    // If it's a throttling exception (the error code will have a string like "throttle" or
                    // "throttling"), also retry.
                    String errorCode = ex.getErrorCode();
                    if (errorCode != null && errorCode.toLowerCase().contains("throttl")) {
                        continue;
                    }

                    // It's a non-retryable error, so wrap the error and re-throw.
                    throw new BridgeServiceException("AWS error verifying sender email: " + ex.getMessage(), ex);
                } catch (Exception ex) {
                    throw new BridgeServiceException("Non-AWS error verifying sender email: " + ex.getMessage(), ex);
                }
            }

            // If we make it this far, then we've exhausted all our retries.
            //noinspection ConstantConditions
            throw new BridgeServiceException("All attempts to verify sender email failed: " +
                    lastException.getMessage(), lastException);
        }
    }
}
