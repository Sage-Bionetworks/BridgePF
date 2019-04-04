package org.sagebionetworks.bridge.models.studies;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadValidationStrictness;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

/**
 * A Bridge study.
 *
 */
// This is required or Jackson searches for, and eventually, finds the same annotation for StudyIdentifer, 
// and attempts to use that to deserialize study (not what you want).
@JsonDeserialize(as=DynamoStudy.class)
public interface Study extends BridgeEntity, StudyIdentifier {
    ObjectWriter STUDY_LIST_WRITER = new BridgeObjectMapper().writer(
        new SimpleFilterProvider().addFilter("filter",
        SimpleBeanPropertyFilter.filterOutAllExcept("name", "identifier")));

    /** Convenience method for creating a Study using a concrete implementation. */
    static Study create() {
        return new DynamoStudy();
    }

    /**
     * The display name of the study (will be seen by participants in email). This name makes the 
     * most sense when it starts with "The".
     */
    String getName();
    void setName(String name);

    /**
     * A short display name for SMS messages and other highly constrained UIs. 10 characters of less. 
     */
    String getShortName();
    void setShortName(String shortName);
    
    /**
     * The name of the institution or research group conducting the study. 
     */
    String getSponsorName();
    void setSponsorName(String sponsorName);
    
    /**
     * A string that uniquely identifies the study, and serves as a domain within which accounts are 
     * scoped for that study. By convention, should be an institution acronym or tag, a dash, and then 
     * an acronym or short phrase for the study. For example "uw-asthma" or "ohsu-molemapper". Cannot
     * be changed once created.
     */
    String getIdentifier();
    void setIdentifier(String identifier);
    
    /**
     * A strongly typed version of the study identifier.
     */
    StudyIdentifier getStudyIdentifier();
    
    /**
     * DynamoDB version number for optimistic locking of record.
     */
    Long getVersion();
    void setVersion(Long version);

    /**
     * Custom events that should be generated for participant upon enrollment. The key in this map is the eventKey, and
     * the value is the offset after the enrollment event (eg, "P1D" for one day after enrollment, "P2W" for 2 weeks
     * after enrollment). Note that this API will automatically pre-pend "custom:" in front of the event key when
     * generating the eventId (eg, eventKey "studyBurstStart" becomes event ID "custom:studyBurstStart").
     */
    Map<String, String> getAutomaticCustomEvents();

    /** @see #getAutomaticCustomEvents */
    void setAutomaticCustomEvents(Map<String, String> automaticCustomEvents);

    /**
     * True if the automatic email verification email on sign-up should be suppressed. False if the email should be
     * sent on sign-up. This is generally used in conjunction with email sign-in, where sending a separate email
     * verification email would be redundant.
     */
    boolean isAutoVerificationEmailSuppressed();

    /** @see #isAutoVerificationEmailSuppressed */
    void setAutoVerificationEmailSuppressed(boolean autoVerificationEmailSuppressed);

    /**
     * True if sessions for unprivileged participant accounts should be locked to an IP address. (Privileged account
     * sessions are _always_ locked to an IP address.)
     */
    boolean isParticipantIpLockingEnabled();

    /** @see #isParticipantIpLockingEnabled */
    void setParticipantIpLockingEnabled(boolean participantIpLockingEnabled);

    /**
     * If true, the channel (email or phone number) used to sign in will be checked and must be verified
     * for sign in to succeed. This is false for legacy studies but set to true for newer studies. 
     */
    boolean isVerifyChannelOnSignInEnabled();
    
    /** @see #isVerifyChannelOnSignInEnabled */
    void setVerifyChannelOnSignInEnabled(boolean verifyChannelOnSignInEnabled);
    
    /**
     * True if we create and return a reauthentication token in the session that can be used to reauthenticate 
     * without a password. False otherwise.
     */
    boolean isReauthenticationEnabled();
    
    void setReauthenticationEnabled(boolean reauthenticationEnabled);
    
    /**
     * User must confirm that they are at least this many years old in order to
     * participate in the study. 
     */
    int getMinAgeOfConsent();
    void setMinAgeOfConsent(int minAge);

    /**
     * <p>
     * True if the Bridge Exporter should include the studyId prefix in the "originalTable" field in the appVersion
     * (now "Health Data Summary") table in Synapse. This exists primarily because we want to remove redundant prefixes
     * from the Synapse tables (to improve reporting), but we don't want to break existing studies or partition
     * existing data.
     * </p>
     * <p>
     * The setting is "reversed" so we don't have to backfill a bunch of old studies.
     * </p>
     * <p>
     * This is a "hidden" setting, primarily to support back-compat for old studies. New studies should be created with
     * this flag set to true, and only admins can change the flag.
     * </p>
     */
    boolean isStudyIdExcludedInExport();

    /** @see #isStudyIdExcludedInExport */
    void setStudyIdExcludedInExport(boolean studyIdExcludedInExport);

    /**
     * The email address that will be given to study participants and other end user for all support 
     * requests and queries (technical, study-related, etc.). This can be a comma-separated list of 
     * email addresses.
     */
    String getSupportEmail();
    void setSupportEmail(String email);

    /** Synapse team ID that is granted read access to exported health data records. */
    Long getSynapseDataAccessTeamId();

    /** @see #getSynapseDataAccessTeamId */
    void setSynapseDataAccessTeamId(Long teamId);

    /** The Synapse project to export health data records to. */
    String getSynapseProjectId();

    /** @see #getSynapseProjectId */
    void setSynapseProjectId(String projectId);

    /**
     * Set a limit on the number of accounts that can be created for this study. This is intended 
     * to establish evaluation studies with limited accounts or enrollment. The value should be 
     * set to 0 for production studies (there is a runtime cost to enforcing this limit). If 
     * value is zero, no limit is enforced.
     */
    int getAccountLimit();
    void setAccountLimit(int accountLimit);
    
    /**
     * The email address for a technical contact who can coordinate with the Bridge Server team on 
     * issues related either to client development or hand-offs of the study data through the 
     * Bridge server. This can be a comma-separated list of email addresses.
     */
    String getTechnicalEmail();
    void setTechnicalEmail(String email);

    /**
     * By default, all studies are exported using the default nightly schedule. Some studies may need custom schedules
     * for hourly or on-demand exports. To prevent this study from being exported twice (once by the custom schedule,
     * once by the default schedule), you should set this attribute to true.
     */
    boolean getUsesCustomExportSchedule();

    /** @see #getUsesCustomExportSchedule */
    void setUsesCustomExportSchedule(boolean usesCustomExportSchedule);

    /**
     * <p>
     * Metadata fields can be configured for any study. This metadata will be implicitly added to every schema and
     * automatically added to every Synapse table.
     * </p>
     * <p>
     * All metadata field definitions are implicitly optional. The "required" field in metadata field definitions is
     * ignored.
     * </p>
     */
    List<UploadFieldDefinition> getUploadMetadataFieldDefinitions();

    /** @see #getUploadMetadataFieldDefinitions */
    void setUploadMetadataFieldDefinitions(List<UploadFieldDefinition> uploadMetadataFieldDefinitions);

    /**
     * How strictly to validate health data and uploads. If this and {@link #isStrictUploadValidationEnabled} are
     * specified, this enum takes precedence.
     */
    UploadValidationStrictness getUploadValidationStrictness();

    /** @see #getUploadValidationStrictness */
    void setUploadValidationStrictness(UploadValidationStrictness uploadValidationStrictness);

    /**
     * Copies of all consent agreements, as well as rosters of all participants in a study, or any 
     * other study governance issues, will be emailed to this address. This can be a comma-separated 
     * list of email addresses. 
     */
    String getConsentNotificationEmail();
    void setConsentNotificationEmail(String email);

    /** True if the consent notification email is verified. False if not. */
    Boolean isConsentNotificationEmailVerified();

    /** @see #isConsentNotificationEmailVerified */
    void setConsentNotificationEmailVerified(Boolean verified);

    /**
     * Extension attributes that can be accepted on the UserProfile object for this study. These 
     * attributes will be exported with the participant roster. 
     */
    Set<String> getUserProfileAttributes();
    void setUserProfileAttributes(Set<String> attributes);

    /**
     * The enumerated task identifiers that can be used when scheduling tasks for this study. These are provided 
     * through the UI to prevent errors when creating schedules. 
     */
    Set<String> getTaskIdentifiers();
    void setTaskIdentifiers(Set<String> taskIdentifiers);

    /**
     * The enumerated activity event keys for timestamps that can be recorded for use when scheduling tasks . These
     * are provided through the UI to prevent errors when creating schedules.
     */
    Set<String> getActivityEventKeys();
    void setActivityEventKeys(Set<String> activityEventKeys);

    /**
     * The enumerated set of data group strings that can be assigned to users in this study. This enumeration ensures 
     * the values are meaningful to the study and the data groups cannot be filled maliciously with junk tags. 
     */
    Set<String> getDataGroups();
    void setDataGroups(Set<String> dataGroups);
    
    /**
     * The password policy for users signing up for this study. 
     */
    PasswordPolicy getPasswordPolicy();
    void setPasswordPolicy(PasswordPolicy passwordPolicy);

    /**
     * The template for emails delivered to users during sign up, asking them to verify their email 
     * address. This template must at least include the "${url}" template variable, which will be 
     * used to place a link back to a page that completes the email verification for Bridge. 
     */
    EmailTemplate getVerifyEmailTemplate();
    void setVerifyEmailTemplate(EmailTemplate template);
    
    /**
     * The template for emails delivered to users who ask to reset their passwords. This template 
     * must at least include the "${url}" template variable, which will be used to place a link 
     * back to a page that completes the password reset request. 
     */
    EmailTemplate getResetPasswordTemplate();
    void setResetPasswordTemplate(EmailTemplate template);
    
    /**
     * The template for an email that will give the user a link in order to sign in to the app 
     * (without having to remember a password). The template must at least include the "${token}" 
     * template variable, which will be used to place a token into a link that must be sent back 
     * to the Bridge server to create a session. 
     */
    EmailTemplate getEmailSignInTemplate();
    void setEmailSignInTemplate(EmailTemplate template);
    
    /**
     * The template for an email that is sent to a user when they sign up for a study using an 
     * existing email address. The email will provide a way to reset the password since the user 
     * appears to have forgotten they have an account. 
     */
    EmailTemplate getAccountExistsTemplate();
    void setAccountExistsTemplate(EmailTemplate template);
    
    /**
     * The template for an email that is sent to a user when they sign a consent agreement to 
     * participate in a study. 
     */
    EmailTemplate getSignedConsentTemplate();
    void setSignedConsentTemplate(EmailTemplate template);
    
    /**
     * The template for an email that is sent to a user when they submit an intent to participate
     * and there is an app install link to send them so they can proceed with onboarding.
     */
    EmailTemplate getAppInstallLinkTemplate();
    void setAppInstallLinkTemplate(EmailTemplate template);
    
    /**
     * The template for an SMS message sent to a user that triggers the reset password workflow, 
     * on an account that only has a phone number.
     */
    SmsTemplate getResetPasswordSmsTemplate();
    void setResetPasswordSmsTemplate(SmsTemplate template);
    
    /**
     * The template for the SMS message sent to a user as part of a sign in via phone workflow.
     */
    SmsTemplate getPhoneSignInSmsTemplate();
    void setPhoneSignInSmsTemplate(SmsTemplate template);
    
    /**
     * The template for the SMS message sent as part of the workflow supported by the intent to 
     * participate API (which will send a link to install the app via the provided phone number).
     */
    SmsTemplate getAppInstallLinkSmsTemplate();
    void setAppInstallLinkSmsTemplate(SmsTemplate template);
    
    /**
     * The template for the SMS message sent to verify and account's phone number.
     */
    SmsTemplate getVerifyPhoneSmsTemplate();
    void setVerifyPhoneSmsTemplate(SmsTemplate template);
    
    /**
     * The template for the SMS message sent as part of the workflow when a user tries to sign up 
     * for a study where they already have an account.
     */
    SmsTemplate getAccountExistsSmsTemplate();
    void setAccountExistsSmsTemplate(SmsTemplate template);
    
    /**
     * The template for an SMS message that is sent to a user when they sign a consent agreement to 
     * participate in a study. 
     */
    SmsTemplate getSignedConsentSmsTemplate();
    void setSignedConsentSmsTemplate(SmsTemplate template);
    
    /**
     * Is this study active? Currently not in use, a de-activated study will be hidden from the 
     * study APIs and will no longer be available for use (a logical delete).
     */
    boolean isActive();
    void setActive(boolean active);

    /** True if uploads in this study should fail on strict validation errors. */
    boolean isStrictUploadValidationEnabled();

    /** @see #isStrictUploadValidationEnabled */
    void setStrictUploadValidationEnabled(boolean enabled);
    
    /** True if we allow users in this study to send an email with a link to sign into the app. */ 
    boolean isEmailSignInEnabled();
    
    /** @see #isEmailSignInEnabled */
    void setEmailSignInEnabled(boolean emailSignInEnabled);
    
    /** True if we allow users in this study to send an SMS message with a token that can be used to sign into the app. */ 
    boolean isPhoneSignInEnabled();
    
    /** @see #isPhoneSignInEnabled */
    void setPhoneSignInEnabled(boolean phoneSignInEnabled);
    
    /** True if this study will export the healthCode when generating a participant roster. */
    boolean isHealthCodeExportEnabled();
    
    /** @see #isHealthCodeExportEnabled(); */
    void setHealthCodeExportEnabled(boolean enabled);
    
    /** True if this study requires users to verify their email addresses in order to sign up. 
     * True by default.
     */
    boolean isEmailVerificationEnabled();
    
    /** @see #isEmailVerificationEnabled(); */
    void setEmailVerificationEnabled(boolean enabled);
    
    /**
     * True if this study will enforce constraints on the external identifier. The ID will have 
     * to be an ID entered into Bridge, it will be assigned to one and only one user, and a user's 
     * ID cannot be changed after it is set. Otherwise, the external ID is just a string field 
     * that can be freely updated.
     */
    boolean isExternalIdValidationEnabled();
    
    /** @see #isExternalIdValidationEnabled(); */
    void setExternalIdValidationEnabled(boolean externalIdValidationEnabled);
    
    /** 
     * True if the external ID must be provided when the user signs up. If validation is also 
     * enabled, this study is configured to use lab codes if desired (username and password auto-
     * generated from the external ID). If this is false, the external ID is not required when 
     * submitting a sign up. 
     */
    boolean isExternalIdRequiredOnSignup();
    
    /** @see #isExternalIdRequiredOnSignup(); */
    void setExternalIdRequiredOnSignup(boolean externalIdRequiredOnSignup);
    
    /**
     * Minimum supported app version number. If set, user app clients pointing to an older version will 
     * fail with an httpResponse status code of 410.
     */
    Map<String, Integer> getMinSupportedAppVersions();
	
	/** @see #getMinSupportedAppVersions(); */
    void setMinSupportedAppVersions(Map<String, Integer> map);
    
    /**
     * A map between operating system names, and the platform ARN necessary to register a device to 
     * receive mobile push notifications for this study, on that platform.
     */
    Map<String, String> getPushNotificationARNs();

    /** @see #getPushNotificationARNs(); */
    void setPushNotificationARNs(Map<String, String> pushNotificationARNs);
    
    /**
     * A map between operating system names, and a link to send via SMS to acquire the study's app. 
     * This can be either to an app store, or an intermediate web page that will route to a final 
     * app or appstore.
     */
    Map<String, String> getInstallLinks();
    
    /** @see #getInstallLinks(); */
    void setInstallLinks(Map<String, String> installLinks);

    /** The flag to disable exporting or not. */
    boolean getDisableExport();

    /** @see #getDisableExport */
    void setDisableExport(boolean disable);
    
    /** Get the OAuth providers for access tokens. */
    Map<String, OAuthProvider> getOAuthProviders();
    
    /** @see #getOAuthProviders */
    void setOAuthProviders(Map<String, OAuthProvider> providers);

    List<AppleAppLink> getAppleAppLinks();
    void setAppleAppLinks(List<AppleAppLink> appleAppLinks);
    
    List<AndroidAppLink> getAndroidAppLinks();
    void setAndroidAppLinks(List<AndroidAppLink> androidAppLinks);
    
    /** If the phone number must be verified, do we suppress sending an SMS message on sign up? */
    boolean isAutoVerificationPhoneSuppressed();
    
    /** Should a new phone number be verified? */
    void setAutoVerificationPhoneSuppressed(boolean autoVerificationPhoneSuppressed);
    
}
