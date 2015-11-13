package org.sagebionetworks.bridge.dynamodb;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;

@DynamoDBTable(tableName = "Study")
@BridgeTypeName("Study")
@JsonFilter("filter")
public final class DynamoStudy implements Study {

    private static final String DOCS_HOST = BridgeConfigFactory.getConfig().getHostnameWithPostfix("docs");
    
    private String name;
    private String sponsorName;
    private String identifier;
    private String stormpathHref;
    private String supportEmail;
    private String technicalEmail;
    private String consentNotificationEmail;
    private int minAgeOfConsent;
    private int maxNumOfParticipants;
    private Long version;
    private boolean active;
    private StudyIdentifier studyIdentifier;
    private Set<String> profileAttributes;
    private Set<String> taskIdentifiers;
    private Set<String> dataGroups;
    private PasswordPolicy passwordPolicy;
    private EmailTemplate verifyEmailTemplate;
    private EmailTemplate resetPasswordTemplate;
    private boolean strictUploadValidationEnabled;
    private boolean healthCodeExportEnabled;

    public DynamoStudy() {
        profileAttributes = new HashSet<>();
        taskIdentifiers = new HashSet<>();
        dataGroups = new HashSet<>();
    }

    /** {@inheritDoc} */
    @Override
    @DynamoDBAttribute
    public String getSponsorName() {
        return sponsorName;
    }

    @Override
    public void setSponsorName(String sponsorName) {
        this.sponsorName = sponsorName;
    }

    /** {@inheritDoc} */
    @Override
    @DynamoDBAttribute
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    /** {@inheritDoc} */
    @Override
    @DynamoDBHashKey
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public void setIdentifier(String identifier) {
        if (identifier != null) {
            this.identifier = identifier;
            this.studyIdentifier = new StudyIdentifierImpl(identifier);
        }
    }

    /** {@inheritDoc} */
    @Override
    @JsonIgnore
    @DynamoDBIgnore
    public StudyIdentifier getStudyIdentifier() {
        return studyIdentifier;
    }

    /** {@inheritDoc} */
    @Override
    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    /** {@inheritDoc} */
    @Override
    public int getMinAgeOfConsent() {
        return minAgeOfConsent;
    }

    @Override
    public void setMinAgeOfConsent(int minAge) {
        this.minAgeOfConsent = minAge;
    }

    /** {@inheritDoc} */
    @Override
    public int getMaxNumOfParticipants() {
        return maxNumOfParticipants;
    }

    @Override
    public void setMaxNumOfParticipants(int maxParticipants) {
        this.maxNumOfParticipants = maxParticipants;
    }

    /** {@inheritDoc} */
    @Override
    public String getStormpathHref() {
        return stormpathHref;
    }

    @Override
    public void setStormpathHref(String stormpathHref) {
        this.stormpathHref = stormpathHref;
    }

    /** {@inheritDoc} */
    @Override
    public String getSupportEmail() {
        return supportEmail;
    }

    @Override
    public void setSupportEmail(String supportEmail) {
        this.supportEmail = supportEmail;
    }

    /** {@inheritDoc} */
    @Override
    public String getTechnicalEmail() {
        return technicalEmail;
    }

    @Override
    public void setTechnicalEmail(String technicalEmail) {
        this.technicalEmail = technicalEmail;
    }

    /** {@inheritDoc} */
    @Override
    public String getConsentNotificationEmail() {
        return consentNotificationEmail;
    }

    @Override
    public void setConsentNotificationEmail(String consentNotificationEmail) {
        this.consentNotificationEmail = consentNotificationEmail;
    }

    /** {@inheritDoc} */
    @DynamoDBMarshalling(marshallerClass = StringSetMarshaller.class)
    @Override
    public Set<String> getUserProfileAttributes() {
        return profileAttributes;
    }

    @Override
    public void setUserProfileAttributes(Set<String> profileAttributes) {
        this.profileAttributes = (profileAttributes == null) ? new HashSet<>() : profileAttributes;
    }

    /** {@inheritDoc} */
    @DynamoDBMarshalling(marshallerClass = StringSetMarshaller.class)
    @Override
    public Set<String> getTaskIdentifiers() {
        return taskIdentifiers;
    }

    @Override
    public void setTaskIdentifiers(Set<String> taskIdentifiers) {
        this.taskIdentifiers = (taskIdentifiers == null) ? new HashSet<>() : taskIdentifiers;
    }
    
    /** {@inheritDoc} */
    @DynamoDBMarshalling(marshallerClass = StringSetMarshaller.class)
    @Override
    public Set<String> getDataGroups() {
        return dataGroups;
    }

    @Override
    public void setDataGroups(Set<String> dataGroups) {
        this.dataGroups = (dataGroups == null) ? new HashSet<>() : dataGroups;
    }
    
    /** {@inheritDoc} */
    @DynamoDBMarshalling(marshallerClass = PasswordPolicyMarshaller.class)
    @Override
    public PasswordPolicy getPasswordPolicy() {
        return passwordPolicy;
    }

    @Override
    public void setPasswordPolicy(PasswordPolicy passwordPolicy) {
        this.passwordPolicy = passwordPolicy;
    }

    /** {@inheritDoc} */
    @DynamoDBMarshalling(marshallerClass = EmailTemplateMarshaller.class)
    @Override
    public EmailTemplate getVerifyEmailTemplate() {
        return verifyEmailTemplate;
    }

    @Override
    public void setVerifyEmailTemplate(EmailTemplate template) {
        this.verifyEmailTemplate = template;
    }

    /** {@inheritDoc} */
    @DynamoDBMarshalling(marshallerClass = EmailTemplateMarshaller.class)
    @Override
    public EmailTemplate getResetPasswordTemplate() {
        return resetPasswordTemplate;
    }

    @Override
    public void setResetPasswordTemplate(EmailTemplate template) {
        this.resetPasswordTemplate = template;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
    }
    
    /** {@inheritDoc} */
    @Override
    @DynamoDBIgnore
    public String getConsentHTML() {
        return String.format("http://%s/%s/consent.html", DOCS_HOST, identifier);
    }

    /** {@inheritDoc} */
    @Override
    @DynamoDBIgnore
    public String getConsentPDF() {
        return String.format("http://%s/%s/consent.pdf", DOCS_HOST, identifier);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isStrictUploadValidationEnabled() {
        return strictUploadValidationEnabled;
    }

    /** {@inheritDoc} */
    @Override
    public void setStrictUploadValidationEnabled(boolean enabled) {
        this.strictUploadValidationEnabled = enabled;
    }
    
    /** {@inheritDoc} */
    @Override
    public boolean isHealthCodeExportEnabled() {
        return healthCodeExportEnabled;
    }

    /** {@inheritDoc} */
    @Override
    public void setHealthCodeExportEnabled(boolean enabled) {
        this.healthCodeExportEnabled = enabled;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hashCode(identifier);
        result = prime * result + Objects.hashCode(maxNumOfParticipants);
        result = prime * result + Objects.hashCode(minAgeOfConsent);
        result = prime * result + Objects.hashCode(name);
        result = prime * result + Objects.hashCode(sponsorName);
        result = prime * result + Objects.hashCode(supportEmail);
        result = prime * result + Objects.hashCode(technicalEmail);
        result = prime * result + Objects.hashCode(consentNotificationEmail);
        result = prime * result + Objects.hashCode(stormpathHref);
        result = prime * result + Objects.hashCode(version);
        result = prime * result + Objects.hashCode(profileAttributes);
        result = prime * result + Objects.hashCode(taskIdentifiers);
        result = prime * result + Objects.hashCode(dataGroups);
        result = prime * result + Objects.hashCode(passwordPolicy);
        result = prime * result + Objects.hashCode(verifyEmailTemplate);
        result = prime * result + Objects.hashCode(resetPasswordTemplate);
        result = prime * result + Objects.hashCode(active);
        result = prime * result + Objects.hashCode(strictUploadValidationEnabled);
        result = prime * result + Objects.hashCode(healthCodeExportEnabled);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DynamoStudy other = (DynamoStudy) obj;

        return (Objects.equals(identifier, other.identifier) && Objects.equals(supportEmail, other.supportEmail)
                && Objects.equals(maxNumOfParticipants, other.maxNumOfParticipants)
                && Objects.equals(minAgeOfConsent, other.minAgeOfConsent) && Objects.equals(name, other.name)
                && Objects.equals(stormpathHref, other.stormpathHref)
                && Objects.equals(passwordPolicy, other.passwordPolicy) && Objects.equals(active, other.active))
                && Objects.equals(verifyEmailTemplate, other.verifyEmailTemplate)
                && Objects.equals(consentNotificationEmail, other.consentNotificationEmail)
                && Objects.equals(resetPasswordTemplate, other.resetPasswordTemplate)
                && Objects.equals(version, other.version)
                && Objects.equals(profileAttributes, other.profileAttributes)
                && Objects.equals(taskIdentifiers, other.taskIdentifiers)
                && Objects.equals(dataGroups, other.dataGroups)
                && Objects.equals(sponsorName, other.sponsorName)
                && Objects.equals(technicalEmail, other.technicalEmail)
                && Objects.equals(strictUploadValidationEnabled, other.strictUploadValidationEnabled)
                && Objects.equals(healthCodeExportEnabled, other.healthCodeExportEnabled);
    }

    @Override
    public String toString() {
        return String.format(
            "DynamoStudy [name=%s, active=%s, sponsorName=%s, identifier=%s, stormpathHref=%s, minAgeOfConsent=%s, "
                            + "maxNumOfParticipants=%s, supportEmail=%s, technicalEmail=%s, consentNotificationEmail=%s, "
                            + "version=%s, userProfileAttributes=%s, taskIdentifiers=%s, dataGroups=%s, passwordPolicy=%s, "
                            + "verifyEmailTemplate=%s, resetPasswordTemplate=%s, strictUploadValidationEnabled=%s, healthCodeExportEnabled=%s]",
            name, active, sponsorName, identifier, stormpathHref, minAgeOfConsent, maxNumOfParticipants,
            supportEmail, technicalEmail, consentNotificationEmail, version, profileAttributes, taskIdentifiers, 
            dataGroups, passwordPolicy, verifyEmailTemplate, resetPasswordTemplate, strictUploadValidationEnabled, 
            healthCodeExportEnabled);
    }
}
