package org.sagebionetworks.bridge.dao;

import javax.annotation.Nonnull;

import org.sagebionetworks.bridge.models.healthdata.HealthDataAttachment;

/** DAO for Health Data Record attachments. */
public interface HealthDataAttachmentDao {
    /**
     * DAO method used by worker apps to create or update health data attachments, generally from unpacking uploads. If
     * the specified attachment has no ID, this is consider a new attachment will be created. If the specified
     * attachment does have an ID, this is considered updating an existing attachment.
     *
     * @param attachment
     *         attachment object to create or update
     * @return ID of the created or updated attachment.
     */
    String createOrUpdateAttachment(@Nonnull HealthDataAttachment attachment);
}
