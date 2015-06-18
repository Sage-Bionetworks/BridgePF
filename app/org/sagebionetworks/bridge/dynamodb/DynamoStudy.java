package org.sagebionetworks.bridge.dynamodb;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

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
    
    private String name;
    private String sponsorName;
    private String identifier;
    private String researcherRole;
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
    private PasswordPolicy passwordPolicy;
    private EmailTemplate verifyEmailTemplate;
    private EmailTemplate resetPasswordTemplate;

    public DynamoStudy() {
        profileAttributes = new HashSet<>();
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
    public String getResearcherRole() {
        return researcherRole;
    }
    @Override
    public void setResearcherRole(String role) {
        this.researcherRole = role;
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
        this.profileAttributes = profileAttributes;
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hashCode(identifier);
        result = prime * result + Objects.hashCode(maxNumOfParticipants);
        result = prime * result + Objects.hashCode(minAgeOfConsent);
        result = prime * result + Objects.hashCode(name);
        result = prime * result + Objects.hashCode(sponsorName);
        result = prime * result + Objects.hashCode(researcherRole);
        result = prime * result + Objects.hashCode(supportEmail);
        result = prime * result + Objects.hashCode(technicalEmail);
        result = prime * result + Objects.hashCode(consentNotificationEmail);
        result = prime * result + Objects.hashCode(stormpathHref);
        result = prime * result + Objects.hashCode(version);
        result = prime * result + Objects.hashCode(profileAttributes);
        result = prime * result + Objects.hashCode(passwordPolicy);
        result = prime * result + Objects.hashCode(verifyEmailTemplate);
        result = prime * result + Objects.hashCode(resetPasswordTemplate);
        result = prime * result + Objects.hashCode(active);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DynamoStudy other = (DynamoStudy) obj;
        
        return (Objects.equals(identifier, other.identifier) && Objects.equals(supportEmail, other.supportEmail) &&
            Objects.equals(maxNumOfParticipants, other.maxNumOfParticipants) && 
            Objects.equals(minAgeOfConsent, other.minAgeOfConsent) && Objects.equals(name, other.name) && 
            Objects.equals(researcherRole, other.researcherRole) && Objects.equals(stormpathHref, other.stormpathHref) &&
            Objects.equals(passwordPolicy, other.passwordPolicy) && Objects.equals(active, other.active)) && 
            Objects.equals(verifyEmailTemplate, other.verifyEmailTemplate) && 
            Objects.equals(consentNotificationEmail, other.consentNotificationEmail) && 
            Objects.equals(resetPasswordTemplate, other.resetPasswordTemplate) &&
            Objects.equals(version, other.version) && Objects.equals(profileAttributes, other.profileAttributes) && 
            Objects.equals(sponsorName, other.sponsorName) && Objects.equals(technicalEmail, other.technicalEmail);
    }

    @Override
    public String toString() {
        return String.format("DynamoStudy [name=%s, active=%s, sponsorName=%s, identifier=%s, researcherRole=%s, stormpathHref=%s, "
            + "minAgeOfConsent=%s, maxNumOfParticipants=%s, supportEmail=%s, technicalEmail=%s, consentNotificationEmail=%s, "
            + "version=%s, userProfileAttributes=%s, passwordPolicy=%s, verifyEmailTemplate=%s, resetPasswordTemplate=%s]",
            name, active, sponsorName, identifier, researcherRole, stormpathHref, minAgeOfConsent, maxNumOfParticipants,
            supportEmail, technicalEmail, consentNotificationEmail, version, profileAttributes, passwordPolicy,
            verifyEmailTemplate, resetPasswordTemplate);        
    }
    
}
