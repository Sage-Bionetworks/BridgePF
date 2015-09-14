package org.sagebionetworks.bridge.services;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.sagebionetworks.bridge.dao.UploadSchemaDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.validators.UploadSchemaValidator;
import org.sagebionetworks.bridge.validators.Validate;

/**
 * Service handler for upload schema APIs. This is called by researchers to create, read, and update upload schemas.
 */
@Component
public class UploadSchemaService {
    private UploadSchemaDao uploadSchemaDao;

    /** DAO for upload schemas. This is configured by Spring. */
    @Autowired
    public void setUploadSchemaDao(UploadSchemaDao uploadSchemaDao) {
        this.uploadSchemaDao = uploadSchemaDao;
    }

    /**
     * <p>
     * Service handler for creating and updating upload schemas. This method creates an upload schema, using the study
     * ID and schema ID of the specified schema, or updates an existing one if it already exists.
     * </p>
     * <p>
     * This method validates the schema. However, it does not validate the study, as that is not user input.
     * </p>
     *
     * @param studyIdentifier
     *         the study this schema should be created or updated in, provided by the controller
     * @param uploadSchema
     *         schema to create or update, must be non-null, must contain a valid schema ID
     * @return the created or updated schema, will be non-null
     */
    public UploadSchema createOrUpdateUploadSchema(StudyIdentifier studyIdentifier, UploadSchema uploadSchema) {
        // validate schema
        if (uploadSchema == null) {
            throw new InvalidEntityException(String.format(Validate.CANNOT_BE_NULL, "upload schema"));
        }
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, uploadSchema);

        // call through to DAO
        return uploadSchemaDao.createOrUpdateUploadSchema(studyIdentifier.getIdentifier(), uploadSchema);
    }

    /**
     * <p>
     * Service handler for deleting an upload schema with the specified study, schema ID, and revision. If the schema
     * doesn't exist, this API throws an EntityNotFoundException.
     * </p>
     * <p>
     * This method validates the schema ID and rev. However, it does not validate the study, as that is not user input.
     * </p>
     *
     * @param studyIdentifier
     *         study to delete the upload schema from, provided by the controller
     * @param schemaId
     *         schema ID of the upload schema to delete, must be non-null and non-empty
     * @param rev
     *         revision number of the upload schema to delete, must be positive
     */
    public void deleteUploadSchemaByIdAndRev(StudyIdentifier studyIdentifier, String schemaId, int rev) {
        if (StringUtils.isBlank(schemaId)) {
            throw new BadRequestException(String.format("Invalid schema ID %s", schemaId));
        }
        if (rev <= 0) {
            throw new BadRequestException("Schema revision must be positive");
        }
        uploadSchemaDao.deleteUploadSchemaByIdAndRev(studyIdentifier, schemaId, rev);
    }

    /**
     * <p>
     * Service handler for deleting all revisions of the upload schema with the specified study and schema ID. If there
     * are no schemas with this schema ID, this API throws an EntityNotFoundException.
     * </p>
     * <p>
     * This method validates the schema ID. However, it does not validate the study, as that is not user input.
     * </p>
     *
     * @param studyIdentifier
     *         study to delete the upload schemas from, provided by the controller
     * @param schemaId
     *         schema ID of the upload schemas to delete, must be non-null and non-empty
     */
    public void deleteUploadSchemaById(StudyIdentifier studyIdentifier, String schemaId) {
        if (StringUtils.isBlank(schemaId)) {
            throw new BadRequestException(String.format("Invalid schema ID %s", schemaId));
        }
        uploadSchemaDao.deleteUploadSchemaById(studyIdentifier, schemaId);
    }

    /**
     * <p>
     * Service handler for fetching upload schemas. This method fetches an upload schema for the specified study and
     * schema ID. If there is more than one revision of the schema, this fetches the latest revision. If the schema
     * doesn't exist, this handler throws an InvalidEntityException.
     * </p>
     * <p>
     * This method validates the schema ID. However, it does not validate the study, as that is not user input.
     * </p>
     *
     * @param studyIdentifier
     *         study to fetch the schema from, provided by the controller
     * @param schemaId
     *         ID of the schema to fetch, must be non-null and non-empty
     * @return the fetched schema, will be non-null
     */
    public UploadSchema getUploadSchema(StudyIdentifier studyIdentifier, String schemaId) {
        if (StringUtils.isBlank(schemaId)) {
            throw new BadRequestException(String.format("Invalid schema ID %s", schemaId));
        }
        return uploadSchemaDao.getUploadSchema(studyIdentifier.getIdentifier(), schemaId);
    }

    /**
     * <p>
     * Service handler for fetching upload schemas. This method fetches all revisions of an an upload schema for 
     * the specified study and schema ID. If the schema doesn't exist, this handler throws an InvalidEntityException.
     * </p>
     * <p>
     * This method validates the schema ID. However, it does not validate the study, as that is not user input.
     * </p>
     *
     * @param studyIdentifier
     *         study to fetch the schema from, provided by the controller
     * @param schemaId
     *         ID of the schema to fetch, must be non-null and non-empty
     * @return the fetched schema, will be non-null
     */
    public List<UploadSchema> getUploadSchemaAllRevisions(StudyIdentifier studyIdentifier, String schemaId) {
        if (StringUtils.isBlank(schemaId)) {
            throw new BadRequestException(String.format("Invalid schema ID %s", schemaId));
        }
        return uploadSchemaDao.getUploadSchemaAllRevisions(studyIdentifier, schemaId);
    }
    
    /**
     * <p>
     * Fetches the upload schema for the specified study, schema ID, and revision. If no schema is found, this API
     * throws an EntityNotFoundException
     * </p>
     * <p>
     * This method validates the schema ID and rev. However, it does not validate the study, as that is not user input.
     * </p>
     *
     * @param studyIdentifier
     *         study to fetch the schema from, provided by the controller
     * @param schemaId
     *         ID of the schema to fetch, must be non-null and non-empty
     * @param schemaRev
     *         revision number of the schema to fetch, must be positive
     * @return the fetched schema, will be non-null
     */
    public UploadSchema getUploadSchemaByIdAndRev(StudyIdentifier studyIdentifier, String schemaId, int schemaRev) {
        if (StringUtils.isBlank(schemaId)) {
            throw new BadRequestException(String.format("Invalid schema ID %s", schemaId));
        }
        if (schemaRev <= 0) {
            throw new BadRequestException(String.format("Invalid schema revision %d", schemaRev));
        }
        return uploadSchemaDao.getUploadSchemaByIdAndRev(studyIdentifier, schemaId, schemaRev);
    }

    /**
     * <p>
     * Service handler for fetching the most recent revision of all upload schemas in a study. This is used by 
     * upload unpacking and validation to match up the data to the schema.
     * </p>
     * <p>
     * This method does not validate the study ID, as that is not user input.
     * </p>
     *
     * @param studyId
     *         study ID to fetch all revisions of all schemas from
     * @return a list of upload schemas
     */
    public List<UploadSchema> getUploadSchemasForStudy(StudyIdentifier studyId) {
        return uploadSchemaDao.getUploadSchemasForStudy(studyId);
    }
}
