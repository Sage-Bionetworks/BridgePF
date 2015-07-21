package org.sagebionetworks.bridge.dao;

import javax.annotation.Nonnull;

import java.util.List;

import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.upload.UploadSchema;

/** DAO for upload schemas. This encapsulates standard CRUD operations as well as list operations. */
public interface UploadSchemaDao {
    /**
     * DAO method for creating and updating upload schemas. This method creates an upload schema, using the study ID
     * and schema ID of the specified schema, or updates an existing one if it already exists.
     *
     * @param studyId
     *         study that the schema should be created or updated in, must be non-null and empty
     * @param uploadSchema
     *         schema to create or update, must be non-null, must contain a valid study ID and a valid schema ID
     * @return the created or updated schema, will be non-null
     */
    @Nonnull UploadSchema createOrUpdateUploadSchema(@Nonnull String studyId, @Nonnull UploadSchema uploadSchema);

    /**
     * DAO method for creating an upload schema from a survey. This is generally called when a survey is published, to
     * create the corresponding upload schema, so that health data records can be created from survey responses.
     * This method will also persist the schema to the backing store.
     *
     * @param studyIdentifier
     *         study that the schema should be created or updated in, must be non-null and empty
     * @param survey
     *         survey to create the upload schema from
     * @return the created upload schema
     */
    @Nonnull UploadSchema createUploadSchemaFromSurvey(@Nonnull StudyIdentifier studyIdentifier,
            @Nonnull Survey survey);

    /**
     * DAO method for deleting an upload schema with the specified study, schema ID, and revision. If the schema
     * doesn't exist, this API throws an EntityNotFoundException.
     *
     * @param studyIdentifier
     *         study to delete the upload schema from, must be non-null
     * @param schemaId
     *         schema ID of the upload schema to delete, must be non-null and non-empty
     * @param rev
     *         revision number of the upload schema to delete, must be positive
     */
    void deleteUploadSchemaByIdAndRev(@Nonnull StudyIdentifier studyIdentifier, @Nonnull String schemaId, int rev);

    /**
     * DAO method for deleting all revisions of the upload schema with the specified study and schema ID. If there are
     * no schemas with this schema ID, this API throws an EntityNotFoundException.
     *
     * @param studyIdentifier
     *         study to delete the upload schemas from, must be non-null
     * @param schemaId
     *         schema ID of the upload schemas to delete, must be non-null and non-empty
     */
    void deleteUploadSchemaById(@Nonnull StudyIdentifier studyIdentifier, @Nonnull String schemaId);

    /**
     * DAO method for fetching upload schemas. This method fetches an upload schema for the specified study and schema
     * ID. If there is more than one revision of the schema, this fetches the latest revision. If the schema doesn't
     * exist, this API throws an EntityNotFoundException.
     *
     * @param studyId
     *         study to fetch the schema from, must be non-null and non-empty
     * @param schemaId
     *         ID of the schema to fetch, must be non-null and non-empty
     * @return the fetched schema, will be non-null
     */
    @Nonnull UploadSchema getUploadSchema(@Nonnull String studyId, @Nonnull String schemaId);

    /**
     * Fetches the upload schema for the specified study, schema ID, and revision. If no schema is found, this API
     * throws an EntityNotFoundException
     *
     * @param studyIdentifier
     *         study to fetch the schema from, must be non-null
     * @param schemaId
     *         ID of the schema to fetch, must be non-null and non-empty
     * @param schemaRev
     *         revision number of the schema to fetch, must be positive
     * @return the fetched schema, will be non-null
     */
    @Nonnull UploadSchema getUploadSchemaByIdAndRev(@Nonnull StudyIdentifier studyIdentifier, @Nonnull String schemaId,
            int schemaRev);

    /**
     * DAO method for fetching all revisions of all upload schemas in a given study. This is used by upload unpacking
     * and validation to match up the data to the schema.
     *
     * @param studyId
     *         study ID to fetch all revisions of all schemas from
     * @return a list of upload schemas
     */
    @Nonnull List<UploadSchema> getUploadSchemasForStudy(@Nonnull StudyIdentifier studyId);
}
