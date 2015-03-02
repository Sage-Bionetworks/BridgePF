package org.sagebionetworks.bridge.models.healthdata;

/**
 * This class is used so that whenever another class (such as the upload validation worker apps) needs to create a
 * prototype HealthDataAttachment object to save into DynamoDB, this class can be used instead of using the Dynamo
 * implementation directly.
 */
public abstract class HealthDataAttachmentBuilder {
    private String id;
    private String recordId;
    private Long version;

    /** @see org.sagebionetworks.bridge.models.healthdata.HealthDataAttachment#getId */
    public String getId() {
        return id;
    }

    /** @see org.sagebionetworks.bridge.models.healthdata.HealthDataAttachment#getId */
    public HealthDataAttachmentBuilder withId(String id) {
        this.id = id;
        return this;
    }

    /** @see org.sagebionetworks.bridge.models.healthdata.HealthDataAttachment#getRecordId */
    public String getRecordId() {
        return recordId;
    }

    /** @see org.sagebionetworks.bridge.models.healthdata.HealthDataAttachment#getRecordId */
    public HealthDataAttachmentBuilder withRecordId(String recordId) {
        this.recordId = recordId;
        return this;
    }

    /** @see org.sagebionetworks.bridge.models.healthdata.HealthDataAttachment#getVersion */
    public Long getVersion() {
        return version;
    }

    /** @see org.sagebionetworks.bridge.models.healthdata.HealthDataAttachment#getVersion */
    public void setVersion(Long version) {
        this.version = version;
    }

    /** Builds and validates a HealthDataAttachment instance. */
    // TODO: This doesn't validate yet, but we should add validation in the future
    public HealthDataAttachment build() {
        return buildUnvalidated();
    }

    /** Builds a HealthDataAttachment object. Subclasses should implement this method. */
    protected abstract HealthDataAttachment buildUnvalidated();
}
