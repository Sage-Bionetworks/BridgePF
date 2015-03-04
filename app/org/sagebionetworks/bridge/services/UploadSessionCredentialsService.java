package org.sagebionetworks.bridge.services;

import java.util.Date;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.services.securitytoken.model.GetSessionTokenRequest;
import com.amazonaws.services.securitytoken.model.GetSessionTokenResult;

/**
 * Generates session-based, temporary credentials.
 */
@Component
public class UploadSessionCredentialsService {

    /**
     * The duration in seconds of which the credentials will remain valid.
     */
    private static final int EXPIRATION_IN_SECONDS = 1200; // 20 minutes

    /**
     * The margin within which we will need to regenerated the credentials
     * before they expire.
     */
    private static final int MARGIN_IN_SECONDS = 300; // 5 minutes

    private final AWSSecurityTokenServiceClient tokenServiceClient;

    private volatile Credentials credentials;

    @Autowired
    public UploadSessionCredentialsService(AWSSecurityTokenServiceClient tokenServiceClient) {
        this.tokenServiceClient = tokenServiceClient;
        credentials = generateCredentials();
    }

    public AWSSessionCredentials getSessionCredentials() {
        Date expiration = credentials.getExpiration();
        Date now = DateTime.now(DateTimeZone.UTC).plusSeconds(MARGIN_IN_SECONDS).toDate();
        if (now.after(expiration)) {
            synchronized(this) {
                expiration = credentials.getExpiration();
                now = DateTime.now(DateTimeZone.UTC).plusSeconds(MARGIN_IN_SECONDS).toDate();
                if (now.after(expiration)) {
                    credentials = generateCredentials();
                }
            }
        }
        return new BasicSessionCredentials(
                credentials.getAccessKeyId(),
                credentials.getSecretAccessKey(),
                credentials.getSessionToken());
    }

    private Credentials generateCredentials() {
        GetSessionTokenRequest getSessionTokenRequest = new GetSessionTokenRequest();
        getSessionTokenRequest.setDurationSeconds(EXPIRATION_IN_SECONDS);
        GetSessionTokenResult sessionTokenResult = tokenServiceClient.getSessionToken(getSessionTokenRequest);
        return sessionTokenResult.getCredentials();
    }
}
