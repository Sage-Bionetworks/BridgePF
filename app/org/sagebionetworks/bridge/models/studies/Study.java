package org.sagebionetworks.bridge.models.studies;

import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.BridgeEntity;

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

    ObjectWriter STUDY_WRITER = new BridgeObjectMapper().writer(
        new SimpleFilterProvider().addFilter("filter", 
        SimpleBeanPropertyFilter.serializeAllExcept("stormpathHref", "active")));

    ObjectWriter STUDY_LIST_WRITER = new BridgeObjectMapper().writer(
        new SimpleFilterProvider().addFilter("filter",
        SimpleBeanPropertyFilter.filterOutAllExcept("name", "identifier")));

    /**
     * The display name of the study (will be seen by participants in email). This name makes the 
     * most sense when it starts with "The".
     */
    String getName();
    void setName(String name);

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
     * User must confirm that they are at least this many years old in order to
     * participate in the study. 
     */
    int getMinAgeOfConsent();
    void setMinAgeOfConsent(int minAge);
    
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
     * Copies of all consent agreements, as well as rosters of all participants in a study, or any 
     * other study governance issues, will be emailed to this address. This can be a comma-separated 
     * list of email addresses. 
     */
    String getConsentNotificationEmail();
    void setConsentNotificationEmail(String email);
    
    /**
     * The URI that identifies the Stormpath directory where all accounts for this study, in a given 
     * environment, will be stored.
     */
    String getStormpathHref();
    void setStormpathHref(String stormpathHref);
    
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
     * Is this study active? Currently not in use, a de-activated study will be hidden from the 
     * study APIs and will no longer be available for use (a logical delete).
     */
    boolean isActive();
    void setActive(boolean active);

    /** True if uploads in this study should fail on strict validation errors. */
    boolean isStrictUploadValidationEnabled();

    /** @see #isStrictUploadValidationEnabled */
    void setStrictUploadValidationEnabled(boolean enabled);
    
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
     * Minimum supported app version number. If set, user app clients pointing to an older version will 
     * fail with an httpResponse status code of 410.
     */
    Map<String, Integer> getMinSupportedAppVersions();
	
	/** @see #getMinSupportedVersion(); */
    void setMinSupportedAppVersions(Map<String, Integer> map);
}
