package org.sagebionetworks.bridge.hibernate;

import javax.persistence.Embeddable;

/**
 * Consent, as represented by Hibernate. Note that this is different from the ConsentSignature class because of how
 * Hibernate marshalls data to and from the database. In particular, it doesn't contain any of the keys (account ID,
 * subpopulation GUID, signedOn). This is because Hibernate doesn't allow us to have table keys in an embeddable'
 * object.
 */
@Embeddable
public final class HibernateAccountConsent {
    private String birthdate;
    private long consentCreatedOn;
    private String name;
    private String signatureImageData;
    private String signatureImageMimeType;
    private Long withdrewOn;

    /** User's birthdate. */
    public String getBirthdate() {
        return birthdate;
    }

    /** @see #getBirthdate */
    public void setBirthdate(String birthdate) {
        this.birthdate = birthdate;
    }

    /**
     * When the consent document was created in the study. Conceptually, this is a foreign key into
     * {@link org.sagebionetworks.bridge.models.subpopulations.StudyConsent#getCreatedOn}
     */
    public long getConsentCreatedOn() {
        return consentCreatedOn;
    }

    /** @see #getConsentCreatedOn */
    public void setConsentCreatedOn(long consentCreatedOn) {
        this.consentCreatedOn = consentCreatedOn;
    }

    /** User's name, as provided at the time of signing consent. */
    public String getName() {
        return name;
    }

    /** @see #getName */
    public void setName(String name) {
        this.name = name;
    }

    /** User's signature, as base64-encoded binary data. */
    public String getSignatureImageData() {
        return signatureImageData;
    }

    /** @see #getSignatureImageData */
    public void setSignatureImageData(String signatureImageData) {
        this.signatureImageData = signatureImageData;
    }

    /** Mime type of {@link #getSignatureImageData}, for example "image/png". */
    public String getSignatureImageMimeType() {
        return signatureImageMimeType;
    }

    /** @see #getSignatureImageMimeType */
    public void setSignatureImageMimeType(String signatureImageMimeType) {
        this.signatureImageMimeType = signatureImageMimeType;
    }

    /** Epoch milliseconds when the user withdrew this consent. Null if the user did not withdraw. */
    public Long getWithdrewOn() {
        return withdrewOn;
    }

    /** @see #getWithdrewOn */
    public void setWithdrewOn(Long withdrewOn) {
        this.withdrewOn = withdrewOn;
    }
}
