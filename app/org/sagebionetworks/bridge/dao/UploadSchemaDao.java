package org.sagebionetworks.bridge.dao;

import javax.annotation.Nonnull;

import org.sagebionetworks.bridge.models.upload.UploadSchema;

/** DAO for upload schemas. This encapsulates standard CRUD operations as well as list operations. */
public interface UploadSchemaDao {
    /**
     * <p>
     * DAO method for creating and updating upload schemas. This method creates an upload schema for the specified
     * study and schema ID, or updates an existing one if it already exists.
     * </p>
     * <p>
     * Preconditions: The study ID and schema ID of the passed in upload schema must match the study ID and schema ID
     * specified in the method args.
     * </p>
     *
     * @param studyId
     *         study to create or update the upload schema under, must be non-null and non-empty
     * @param schemaId
     *         ID of the schema to create or update, must be non-null and non-empty
     * @param uploadSchema
     *         schema to create or update, must be non-null
     * @return the created or updated schema, will be non-null
     */
    @Nonnull UploadSchema createOrUpdateUploadSchema(@Nonnull String studyId, @Nonnull String schemaId,
            @Nonnull UploadSchema uploadSchema);

    /**
     * DAO method for fetching upload schemas. This method fetches an upload schema for the specified study and schema
     * ID. f there is more than one revision of the schema, this fetches the latest revision. If the schema doesn't
     * exist, this API throws an InvalidEntityException.
     *
     * @param studyId
     *         study to fetch the schema from, must be non-null and non-empty
     * @param schemaId
     *         ID of the schema to fetch, must be non-null and non-empty
     * @return the fetched schema, will be non-null
     */
    @Nonnull UploadSchema getUploadSchema(@Nonnull String studyId, @Nonnull String schemaId);

    // TODO add list and delete APIs
}
