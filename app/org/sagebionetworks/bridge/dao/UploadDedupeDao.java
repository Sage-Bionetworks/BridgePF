package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.schema.UploadSchemaKey;

/**
 * DAO to answer the simple question: Given some upload attributes (healthCode, createdOn, schemaKey), is this upload a
 * duplicate?
 */
public interface UploadDedupeDao {
    /**
     * <p>
     * Given these upload attributes, determine if the upload is a duplicate of a pre-existing upload. An upload is a
     * duplicate if has the same healthCode (user) and schemaKey (activity) as a previous upload as was created within
     * a certain configurable time threshold.
     * </p>
     * <p>
     * Note that this method is read only and has no side effects. If you want to write to UploadDedupe so that future
     * calls recognize the upload as a dupe, call {@link #registerUpload}.
     * </p>
     *
     * @param createdOn
     *         epoch milliseconds the upload was created (createdOn timestamp). This is a long instead of a DateTime
     *         because HealthDataRecord stores this as a long
     * @param healthCode
     *         user's health code
     * @param schemaKey
     *         schema key (activity)
     * @return true if the upload is a duplicate
     */
    boolean isDuplicate(long createdOn, String healthCode, UploadSchemaKey schemaKey);

    /**
     * Writes upload attributes to the DAO so that future calls to {@link #isDuplicate} recognize these attributes as
     * duplicates.
     *
     * @param createdOn
     *         epoch milliseconds the upload was created (createdOn timestamp)
     * @param healthCode
     *         user's health code
     * @param schemaKey
     *         schema key (activity)
     * @param uploadId
     *         ID of the upload, for use in future offline analysis
     */
    void registerUpload(long createdOn, String healthCode, UploadSchemaKey schemaKey, String uploadId);
}
