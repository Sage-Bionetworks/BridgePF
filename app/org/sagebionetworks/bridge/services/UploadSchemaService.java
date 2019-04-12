package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.UploadSchemaDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleMetadata;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.models.upload.UploadSchemaType;
import org.sagebionetworks.bridge.upload.UploadUtil;
import org.sagebionetworks.bridge.validators.UploadSchemaValidator;
import org.sagebionetworks.bridge.validators.Validate;

/**
 * Service handler for upload schema APIs. This is called by researchers to create, read, and update upload schemas.
 */
@Component
public class UploadSchemaService {
    private SharedModuleMetadataService sharedModuleMetadataService;
    private UploadSchemaDao uploadSchemaDao;

    /** DAO for upload schemas. This is configured by Spring. */
    @Autowired
    public final void setUploadSchemaDao(UploadSchemaDao uploadSchemaDao) {
        this.uploadSchemaDao = uploadSchemaDao;
    }

    @Autowired
    public final void setSharedModuleMetadataService(SharedModuleMetadataService sharedModuleMetadataService) {
        this.sharedModuleMetadataService = sharedModuleMetadataService;
    }

    /**
     * Creates a schema revision using the new V4 semantics. The schema ID and revision will be taken from the
     * UploadSchema object. If the revision isn't specified, we'll get the latest schema rev for the schema ID and use
     * that rev + 1.
     */
    public UploadSchema createSchemaRevisionV4(StudyIdentifier studyId, UploadSchema schema) {
        // Controller guarantees valid studyId and non-null uploadSchema
        checkNotNull(studyId, "studyId must be non-null");
        checkNotNull(schema, "uploadSchema must be non-null");

        // Schema ID is validated by getCurrentSchemaRevision()

        // Set revision if needed. 0 represents an unset schema rev.
        int oldRev = getCurrentSchemaRevision(studyId, schema.getSchemaId());
        if (schema.getRevision() == 0) {
            schema.setRevision(oldRev + 1);
        }

        // Set study. This enforces that you can't create schemas outside of your study.
        schema.setStudyId(studyId.getIdentifier());

        // validate schema
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);

        // call through to DAO
        return uploadSchemaDao.createSchemaRevision(schema);
    }

    /**
     * <p>
     * Service handler for creating and updating upload schemas. This method creates an upload schema, using the study
     * ID and schema ID of the specified schema, or updates an existing one if it already exists.
     * </p>
     * <p>
     * This uses the old V3 semantics for creating and updating schemas. To create a schema, do not set the revision
     * (or set it to the default value of 0). To update a schema, set revision equal to the latest revision of the
     * schema. Creating a schema that already exists, or updating a schema that's not the latest revision will result
     * in a ConcurrentModificationException.
     * </p>
     */
    public UploadSchema createOrUpdateUploadSchema(StudyIdentifier studyId, UploadSchema schema) {
        // Controller guarantees valid studyId and non-null uploadSchema
        checkNotNull(studyId, "studyId must be non-null");
        checkNotNull(schema, "uploadSchema must be non-null");

        // Schema ID is validated by getCurrentSchemaRevision()

        // Request should match old rev. If it does, auto-increment and write. Otherwise, throw.
        int oldRev = getCurrentSchemaRevision(studyId, schema.getSchemaId());
        if (oldRev != schema.getRevision()) {
            throw new ConcurrentModificationException(schema);
        }
        schema.setRevision(oldRev + 1);

        // Set study. This enforces that you can't create schemas outside of your study.
        schema.setStudyId(studyId.getIdentifier());

        // validate schema
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);

        // call through to DAO
        return uploadSchemaDao.createSchemaRevision(schema);
    }

    /**
     * Private helper function to get the current schema revision of the specified schema ID. Returns 0 if the schema
     * doesn't exist. This is generally useful for validating the proper revision number for making updates.
     */
    private int getCurrentSchemaRevision(StudyIdentifier studyId, String schemaId) {
        UploadSchema oldSchema = getUploadSchemaNoThrow(studyId, schemaId);
        if (oldSchema != null) {
            return oldSchema.getRevision();
        } else {
            return 0;
        }
    }

    /**
     * <p>
     * Creates an upload schema from a survey. This is generally called when a survey is published, to
     * create the corresponding upload schema, so that health data records can be created from survey responses.
     * This method will also persist the schema to the backing store.
     * <p>
     * If newSchemaRev is true, this method will always create a new schema revision. If false, it will attempt to
     * modify the existing schema revision. However, if the schema revisions are not compatible, it will fall back to
     * creating a new schema revision.
     * </p>
     */
    public UploadSchema createUploadSchemaFromSurvey(StudyIdentifier studyId, Survey survey, boolean newSchemaRev) {
        // https://sagebionetworks.jira.com/browse/BRIDGE-1698 - If the existing Schema ID points to a different survey
        // or a non-survey, this is an error. Having multiple surveys point to the same schema ID causes really bad
        // things to happen, and we need to prevent it.
        String schemaId = survey.getIdentifier();
        UploadSchema oldSchema = getUploadSchemaNoThrow(studyId, schemaId);
        if (oldSchema != null) {
            if (oldSchema.getSchemaType() != UploadSchemaType.IOS_SURVEY ||
                    !Objects.equals(oldSchema.getSurveyGuid(), survey.getGuid())) {
                throw new BadRequestException("Survey with identifier " + schemaId +
                        " conflicts with schema with the same ID. Please use a different survey identifier.");
            }
        }

        // If we want to use the existing schema rev, and one exists. Note that we've already validated that it is for
        // the same survey.
        if (!newSchemaRev && oldSchema != null) {
            // Check that the old schema already has the answers field.
            List<UploadFieldDefinition> oldFieldDefList = oldSchema.getFieldDefinitions();
            UploadFieldDefinition answersFieldDef = oldFieldDefList.stream()
                    .filter(fieldDef -> UploadUtil.FIELD_ANSWERS.equals(fieldDef.getName()))
                    .findFirst().orElse(null);

            if (answersFieldDef == null) {
                // Old schema doesn't have the
                List<UploadFieldDefinition> newFieldDefList = new ArrayList<>(oldFieldDefList);
                newFieldDefList.add(UploadUtil.ANSWERS_FIELD_DEF);
                addSurveySchemaMetadata(oldSchema, survey);
                oldSchema.setFieldDefinitions(newFieldDefList);
                return updateSchemaRevisionV4(studyId, schemaId, oldSchema.getRevision(), oldSchema);
            }

            // Answers field needs to be either
            // (a) an attachment (Large Text or normal)
            // (b) a string with isUnboundedLength=true
            UploadFieldType fieldType = answersFieldDef.getType();
            if (fieldType == UploadFieldType.LARGE_TEXT_ATTACHMENT ||
                    UploadFieldType.ATTACHMENT_TYPE_SET.contains(fieldType) ||
                    (UploadFieldType.STRING_TYPE_SET.contains(fieldType) &&
                            Boolean.TRUE.equals(answersFieldDef.isUnboundedText()))) {
                // The old schema works for the new survey. However, we want to ensure the old schema points to the
                // latest version of the survey. Update survey metadata in the schema.
                addSurveySchemaMetadata(oldSchema, survey);
                return updateSchemaRevisionV4(studyId, schemaId, oldSchema.getRevision(), oldSchema);
            }

            // If execution gets this far, that means we have a schema with an "answers" field that's not compatible.
            // At this point, we go into the branch that creates a new schema, below.
        }

        // We were unable to reconcile this with the existing schema. Create a new schema. (Create API will
        // automatically bump the rev number if an old schema revision exists.)
        UploadSchema schemaToCreate = UploadSchema.create();
        addSurveySchemaMetadata(schemaToCreate, survey);
        schemaToCreate.setFieldDefinitions(ImmutableList.of(UploadUtil.ANSWERS_FIELD_DEF));
        return createSchemaRevisionV4(studyId, schemaToCreate);
    }

    // Helper method to add survey fields to schemas. This is useful so we have the same attributes for newly created
    // schemas, as well as setting them into updated schemas.
    private static void addSurveySchemaMetadata(UploadSchema schema, Survey survey) {
        // No need to set rev or version unless updating an existing rev. Study is always taken care of by the APIs.
        schema.setName(survey.getName());
        schema.setSchemaId(survey.getIdentifier());
        schema.setSchemaType(UploadSchemaType.IOS_SURVEY);
        schema.setSurveyGuid(survey.getGuid());
        schema.setSurveyCreatedOn(survey.getCreatedOn());
    }

    /**
     * Service handler for deleting all revisions of the upload schema with the specified study and schema ID. If there
     * are no schemas with this schema ID, this API throws an EntityNotFoundException.
     */
    public void deleteUploadSchemaById(StudyIdentifier studyId, String schemaId) {
        // Schema ID is validated by getUploadSchemaAllRevisions()

        List<UploadSchema> schemaList = getSchemaRevisionsForDelete(studyId, schemaId);
        uploadSchemaDao.deleteUploadSchemas(schemaList);
    }

    public void deleteUploadSchemaByIdPermanently(StudyIdentifier studyId, String schemaId) {
        // Schema ID is validated by getUploadSchemaAllRevisions()

        List<UploadSchema> schemaList = getSchemaRevisionsForDelete(studyId, schemaId);
        uploadSchemaDao.deleteUploadSchemasPermanently(schemaList);
    }

    protected List<UploadSchema> getSchemaRevisionsForDelete(StudyIdentifier studyId, String schemaId) {
        List<UploadSchema> schemaList = getUploadSchemaAllRevisions(studyId, schemaId, true);

        List<Integer> revisions = new ArrayList<>();
        for (int i = 0; i < schemaList.size(); i++) {
            revisions.add(schemaList.get(i).getRevision());
        }
        Map<String,Object> parameters = new HashMap<>();
        parameters.put("schemaId", schemaId);
        parameters.put("schemaRevisions", revisions);

        List<SharedModuleMetadata> sharedModuleMetadataList = sharedModuleMetadataService.queryAllMetadata(false, false,
                "schemaId=:schemaId AND schemaRevision IN :schemaRevisions", parameters, null, true);

        if (sharedModuleMetadataList.size() != 0) {
            throw new BadRequestException("Cannot delete specified Upload Schema because a shared module still refers to it.");
        }
        return schemaList;
    }
    
    /**
     * Service handler for deleting an upload schema with the specified study, schema ID, and revision. If the schema
     * doesn't exist, this API throws an EntityNotFoundException.
     */
    public void deleteUploadSchemaByIdAndRevision(StudyIdentifier studyId, String schemaId, int rev) {
        // Schema ID and rev are validated by getUploadSchemaByIdAndRev()

        UploadSchema schema = getRevisionForDeletion(studyId, schemaId, rev);
        if (schema == null || schema.isDeleted()) {
            throw new EntityNotFoundException(UploadSchema.class);
        }
        uploadSchemaDao.deleteUploadSchemas(ImmutableList.of(schema));    
    }
    
    public void deleteUploadSchemaByIdAndRevisionPermanently(StudyIdentifier studyId, String schemaId, int rev) {
        // Schema ID and rev are validated by getUploadSchemaByIdAndRev()

        UploadSchema schema = getRevisionForDeletion(studyId, schemaId, rev);
        if (schema == null) {
            throw new EntityNotFoundException(UploadSchema.class);
        }
        uploadSchemaDao.deleteUploadSchemasPermanently(ImmutableList.of(schema));    
    }

    /** Returns all revisions of all schemas. */
    public List<UploadSchema> getAllUploadSchemasAllRevisions(StudyIdentifier studyId, boolean includeDeleted) {
        return uploadSchemaDao.getAllUploadSchemasAllRevisions(studyId, includeDeleted);
    }

    /** Service handler for fetching the most recent revision of all upload schemas in a study. */
    public List<UploadSchema> getUploadSchemasForStudy(StudyIdentifier studyId, boolean includeDeleted) {
        // Get all schemas. No simple query for just latest schemas.
        List<UploadSchema> allSchemasAllRevisions = getAllUploadSchemasAllRevisions(studyId, includeDeleted);

        // Iterate schemas and pick out latest for each schema ID.
        // Find the most recent version of each schema with a unique schemaId
        Map<String,UploadSchema> schemaMap = new HashMap<>();
        for (UploadSchema schema : allSchemasAllRevisions) {
            UploadSchema existing = schemaMap.get(schema.getSchemaId());
            if (existing == null || schema.getRevision() > existing.getRevision()) {
                schemaMap.put(schema.getSchemaId(), schema);
            }
        }
        // Do we care if it's sorted? What would it be sorted by?
        return ImmutableList.copyOf(schemaMap.values());
    }

    /**
     * Service handler for fetching upload schemas. This method fetches an upload schema for the specified study and
     * schema ID. If there is more than one revision of the schema, this fetches the latest revision. If the schema
     * doesn't exist, this handler throws an InvalidEntityException.
     */
    public UploadSchema getUploadSchema(StudyIdentifier studyId, String schemaId) {
        UploadSchema schema = getUploadSchemaNoThrow(studyId, schemaId);
        if (schema == null) {
            throw new EntityNotFoundException(UploadSchema.class);
        }
        return schema;
    }

    /**
     * Private helper method to get the latest version of an upload schema, but doesn't throw if the schema does not
     * exist. Note that it still validates the user inputs (schemaId) and will throw a BadRequestException.
     */
    private UploadSchema getUploadSchemaNoThrow(StudyIdentifier studyId, String schemaId) {
        if (StringUtils.isBlank(schemaId)) {
            throw new BadRequestException("Schema ID must be specified");
        }
        return uploadSchemaDao.getUploadSchemaLatestRevisionById(studyId, schemaId);
    }
    
    /**
     * Private helper method to get the latest version of an upload schema, but doesn't throw if the schema does not
     * exist. User inputs are validated (schemaId and revision) and the method will throw a BadRequestException if 
     * the schema is referenced as part of shared module metadata.
     */
    private UploadSchema getRevisionForDeletion(StudyIdentifier studyId, String schemaId, int revision) {
        if (StringUtils.isBlank(schemaId)) {
            throw new BadRequestException("Schema ID must be specified");
        }
        if (revision <= 0) {
            throw new BadRequestException("Revision must be specified and positive");
        }
        UploadSchema schema = uploadSchemaDao.getUploadSchemaByIdAndRevision(studyId, schemaId, revision);
        if (schema != null) {
            Map<String,Object> parameters = new HashMap<>();
            parameters.put("schemaId", schemaId);
            parameters.put("schemaRevision", revision);

            List<SharedModuleMetadata> sharedModuleMetadataList = sharedModuleMetadataService.queryAllMetadata(false, false,
                    "schemaId=:schemaId AND schemaRevision=:schemaRevision", parameters, null, true);

            if (sharedModuleMetadataList.size() != 0) {
                throw new BadRequestException("Cannot delete specified Upload Schema because a shared module still refers to it.");
            }
        }
        return schema;
    }

    /**
     * Service handler for fetching upload schemas. This method fetches all revisions of an an upload schema for
     * the specified study and schema ID. If the schema doesn't exist, this handler throws an EntityNotFoundException.
     */
    public List<UploadSchema> getUploadSchemaAllRevisions(StudyIdentifier studyId, String schemaId, boolean includeDeleted) {
        if (StringUtils.isBlank(schemaId)) {
            throw new BadRequestException("Schema ID must be specified");
        }

        List<UploadSchema> schemaList = uploadSchemaDao.getUploadSchemaAllRevisionsById(studyId, schemaId, includeDeleted);
        if (schemaList.isEmpty()) {
            throw new EntityNotFoundException(UploadSchema.class);
        }
        return schemaList;
    }

    /**
     * Fetches the upload schema for the specified study, schema ID, and revision. If no schema is found, this API
     * throws an EntityNotFoundException
     */
    public UploadSchema getUploadSchemaByIdAndRev(StudyIdentifier studyId, String schemaId, int revision) {
        if (StringUtils.isBlank(schemaId)) {
            throw new BadRequestException("Schema ID must be specified");
        }
        if (revision <= 0) {
            throw new BadRequestException("Revision must be specified and positive");
        }

        UploadSchema schema = uploadSchemaDao.getUploadSchemaByIdAndRevision(studyId, schemaId, revision);
        if (schema == null) {
            throw new EntityNotFoundException(UploadSchema.class, "Can't find schema " + schemaId + "-v" + revision);
        }
        return schema;
    }

    /**
     * Gets the latest available revision of the specified schema for the specified client. This API fetches every
     * schema revision for the specified schema ID, then checks the schema's min/maxAppVersion against the clientInfo.
     * If multiple schema revisions match, it returns the latest one.
     */
    public UploadSchema getLatestUploadSchemaRevisionForAppVersion(StudyIdentifier studyId, String schemaId,
            ClientInfo clientInfo) {
        checkNotNull(studyId, "Study ID must be specified");
        checkNotNull(clientInfo, "Client Info must be specified");

        List<UploadSchema> schemaList = getUploadSchemaAllRevisions(studyId, schemaId, false);
        return schemaList.stream().filter(schema -> isSchemaAvailableForClientInfo(schema, clientInfo))
                .max((schema1, schema2) -> Integer.compare(schema1.getRevision(), schema2.getRevision())).orElse(null);
    }

    // Helper method which checks if a schema is available for a client, by checking the schema's min/maxAppVersion
    // against the client's OS and appVersion.
    //
    // This filter is permissive. If neither the ClientInfo nor the constraints in the schema exclude this schema,
    // then the schema is available.
    //
    // Package-scoped to facilitate unit tests.
    static boolean isSchemaAvailableForClientInfo(UploadSchema schema, ClientInfo clientInfo) {
        String osName = clientInfo.getOsName();
        Integer appVersion = clientInfo.getAppVersion();
        if (osName != null && appVersion != null) {
            Integer minAppVersion = schema.getMinAppVersion(osName);
            if (minAppVersion != null && appVersion < minAppVersion) {
                return false;
            }

            Integer maxAppVersion = schema.getMaxAppVersion(osName);
            if (maxAppVersion != null && appVersion > maxAppVersion) {
                return false;
            }
        }

        // Permissive filter defaults to true.
        return true;
    }

    /**
     * <p>
     * Updates a schema rev using V4 semantics. This also validates that the schema changes are legal. Legal changes
     * means schema fields cannot be deleted or modified.
     * </p>
     * <p>
     * Updating a schema revision that doesn't exist throws an EntityNotFoundException.
     * </p>
     */
    public UploadSchema updateSchemaRevisionV4(StudyIdentifier studyId, String schemaId, int revision,
            UploadSchema schemaToUpdate) {
        // Controller guarantees valid studyId and non-null uploadSchema
        checkNotNull(studyId, "studyId must be non-null");
        checkNotNull(schemaToUpdate, "uploadSchema must be non-null");

        // Get existing schema revision. This also validates schema ID and rev and throws if the schema revision
        // doesn't exist.
        UploadSchema oldSchema = getUploadSchemaByIdAndRev(studyId, schemaId, revision);
        if (oldSchema.isDeleted() && schemaToUpdate.isDeleted()) {
            throw new EntityNotFoundException(UploadSchema.class);
        }
        // Schemas associated with shared module can't be modified.
        if (StringUtils.isNotBlank(oldSchema.getModuleId())) {
            throw new BadRequestException("Schema " + oldSchema.getSchemaId() + " was imported from a shared module " +
                    "and cannot be modified.");
        }

        // Set study ID, schema ID, and revision. This ensures we are updating the correct schema in the correct study.
        schemaToUpdate.setStudyId(studyId.getIdentifier());
        schemaToUpdate.setSchemaId(schemaId);
        schemaToUpdate.setRevision(revision);

        // validate schema
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schemaToUpdate);

        // Get field names for old and new schema and compute the fields that have been deleted or retained.
        List<String> errorMessageList = new ArrayList<>();

        Map<String, UploadFieldDefinition> oldFieldMap = getFieldsByName(oldSchema);
        Set<String> oldFieldNameSet = oldFieldMap.keySet();

        Map<String, UploadFieldDefinition> newFieldMap = getFieldsByName(schemaToUpdate);
        Set<String> newFieldNameSet = newFieldMap.keySet();

        Set<String> deletedFieldNameSet = Sets.difference(oldFieldNameSet, newFieldNameSet);
        Set<String> retainedFieldNameSet = Sets.intersection(oldFieldNameSet, newFieldNameSet);

        // Check deleted fields.
        if (!deletedFieldNameSet.isEmpty()) {
            errorMessageList.add("Can't delete fields: " + BridgeUtils.COMMA_SPACE_JOINER.join(deletedFieldNameSet));
        }

        // Check retained fields, make sure none are modified.
        Set<String> incompatibleFieldNameSet = new TreeSet<>();
        for (String oneRetainedFieldName : retainedFieldNameSet) {
            UploadFieldDefinition oldFieldDef = oldFieldMap.get(oneRetainedFieldName);
            UploadFieldDefinition newFieldDef = newFieldMap.get(oneRetainedFieldName);

            if (!UploadUtil.isCompatibleFieldDef(oldFieldDef, newFieldDef)) {
                incompatibleFieldNameSet.add(oneRetainedFieldName);
            }
        }
        if (!incompatibleFieldNameSet.isEmpty()) {
            errorMessageList.add("Incompatible changes to fields: " + BridgeUtils.COMMA_SPACE_JOINER.join(
                    incompatibleFieldNameSet));
        }

        // Can't modify schema types.
        if (oldSchema.getSchemaType() != schemaToUpdate.getSchemaType()) {
            errorMessageList.add("Can't modify schema type, old=" + oldSchema.getSchemaType() + ", new=" +
                    schemaToUpdate.getSchemaType());
        }

        // If we have any errors, concat them together and throw a 400 bad request.
        if (!errorMessageList.isEmpty()) {
            throw new BadRequestException("Can't update study " + studyId.getIdentifier() + " schema " + schemaId +
                    " revision " + revision + ": " + BridgeUtils.SEMICOLON_SPACE_JOINER.join(errorMessageList));
        }

        // Call through to the DAO
        return uploadSchemaDao.updateSchemaRevision(schemaToUpdate);
    }

    // Helper method to get a map of fields by name for an Upload Schema. Returns a TreeMap so our error messaging has
    // the fields in a consistent order.
    private static Map<String, UploadFieldDefinition> getFieldsByName(UploadSchema uploadSchema) {
        Map<String, UploadFieldDefinition> fieldsByName = new TreeMap<>();
        fieldsByName.putAll(Maps.uniqueIndex(uploadSchema.getFieldDefinitions(), UploadFieldDefinition::getName));
        return fieldsByName;
    }
}
