package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.WORKER;

import java.util.EnumSet;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.UploadSchemaService;

/** Play controller for the /researcher/v1/uploadSchema APIs */
@Controller
public class UploadSchemaController extends BaseController {
    private UploadSchemaService uploadSchemaService;

    /** Service handler for Upload Schema APIs. This is configured by Spring. */
    @Autowired
    public void setUploadSchemaService(UploadSchemaService uploadSchemaService) {
        this.uploadSchemaService = uploadSchemaService;
    }

    /**
     * Service handler for creating a new schema revision, using V4 API semantics. See
     * {@link org.sagebionetworks.bridge.dao.UploadSchemaDao#createSchemaRevisionV4}
     *
     * @return Play result, with the created schema
     */
    public Result createSchemaRevisionV4() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();

        UploadSchema uploadSchema = parseJson(request(), UploadSchema.class);
        UploadSchema createdSchema = uploadSchemaService.createSchemaRevisionV4(studyId, uploadSchema);
        return created(UploadSchema.PUBLIC_SCHEMA_WRITER.writeValueAsString(createdSchema));
    }

    /**
     * Play controller for POST /researcher/v1/uploadSchema/:schemaId. This API creates an upload schema, using the
     * study for the current service endpoint and the schema of the specified schema. If the schema already exists,
     * this method updates it instead.
     *
     * @return Play result, with the created or updated schema in JSON format
     */
    public Result createOrUpdateUploadSchema() throws JsonProcessingException {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        UploadSchema uploadSchema = parseJson(request(), UploadSchema.class);
        UploadSchema createdSchema = uploadSchemaService.createOrUpdateUploadSchema(studyId, uploadSchema);
        return ok(UploadSchema.PUBLIC_SCHEMA_WRITER.writeValueAsString(createdSchema));
    }

    /**
     * Play controller for DELETE /researcher/v1/uploadSchema/byIdAndRev/:schemaId/:rev. This API deletes an upload
     * schema with the specified schema ID and revision. If the schema doesn't exist, this API throws a 404 exception.
     *
     * @param schemaId
     *         schema ID of the upload schema to delete
     * @param rev
     *         revision number of the upload schema to delete, must be positive
     * @return Play result with the OK message
     */
    public Result deleteUploadSchemaByIdAndRev(String schemaId, int rev) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyIdentifier = session.getStudyIdentifier();

        uploadSchemaService.deleteUploadSchemaByIdAndRev(studyIdentifier, schemaId, rev);
        return okResult("Schema has been deleted.");
    }

    /**
     * Play controller for DELETE /researcher/v1/uploadSchema/byId/:schemaId. This API deletes all revisions of the
     * upload schema with the specified schema ID. If there are no schemas with this schema ID, this API throws a 404
     * exception.
     *
     * @param schemaId
     *         schema ID of the upload schemas to delete, must be non-null and non-empty
     * @return Play result with the OK message
     */
    public Result deleteUploadSchemaById(String schemaId) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyIdentifier = session.getStudyIdentifier();

        uploadSchemaService.deleteUploadSchemaById(studyIdentifier, schemaId);
        return okResult("Schemas have been deleted.");
    }

    /**
     * Play controller for GET /researcher/v1/uploadSchema/byId/:schemaId. This API fetches the upload schema with the
     * specified ID. If there is more than one revision of the schema, this fetches the latest revision. If the schema
     * doesn't exist, this API throws a 404 exception.
     *
     * @param schemaId
     *         schema ID to fetch
     * @return Play result with the fetched schema in JSON format
     */
    public Result getUploadSchema(String schemaId) throws JsonProcessingException {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        UploadSchema uploadSchema = uploadSchemaService.getUploadSchema(studyId, schemaId);
        return ok(UploadSchema.PUBLIC_SCHEMA_WRITER.writeValueAsString(uploadSchema));
    }
    
    /**
     * Play controller for GET /v3/uploadschemas/:schemaId. Returns all revisions of this upload schema. If the 
     * schema doesn't exist, this API throws a 404 exception.
     * @param schemaId
     *         schema ID to fetch
     * @return Play result with an array of all revisions of the fetched schema in JSON format
     */
    public Result getUploadSchemaAllRevisions(String schemaId) throws JsonProcessingException {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        List<UploadSchema> uploadSchemas = uploadSchemaService.getUploadSchemaAllRevisions(studyId, schemaId);
        ResourceList<UploadSchema> uploadSchemaResourceList = new ResourceList<>(uploadSchemas);
        return ok(UploadSchema.PUBLIC_SCHEMA_WRITER.writeValueAsString(uploadSchemaResourceList));
    }

    /**
     * Fetches the upload schema for the specified study, schema ID, and revision. If no schema is found, this API
     * throws a 404 exception.
     *
     * @param schemaId
     *         schema ID to fetch
     * @param rev
     *         revision number of the schema to fetch, must be positive
     * @return Play result with the fetched schema in JSON format
     */
    public Result getUploadSchemaByIdAndRev(String schemaId, int rev) throws JsonProcessingException {
        UserSession session = getAuthenticatedSession(EnumSet.of(DEVELOPER, WORKER));
        StudyIdentifier studyId = session.getStudyIdentifier();

        UploadSchema uploadSchema = uploadSchemaService.getUploadSchemaByIdAndRev(studyId, schemaId, rev);
        return ok(UploadSchema.PUBLIC_SCHEMA_WRITER.writeValueAsString(uploadSchema));
    }

    /**
     * Cross-study worker API to get the upload schema for the specified study, schema ID, and revision.
     *
     * @param studyId
     *         study the schema lives in
     * @param schemaId
     *         schema to fetch
     * @param revision
     *         schema revision to fetch
     * @return the requested schema revision
     */
    public Result getUploadSchemaByStudyAndSchemaAndRev(String studyId, String schemaId, int revision) {
        getAuthenticatedSession(WORKER);
        UploadSchema uploadSchema = uploadSchemaService.getUploadSchemaByIdAndRev(new StudyIdentifierImpl(studyId),
                schemaId, revision);
        return okResult(uploadSchema);
    }

    /**
     * Play controller for GET /v3/uploadschemas. This API fetches the most recent revision of all upload 
     * schemas for the current study. 
     * 
     * @return Play result with list of schemas for this study
     */
    public Result getUploadSchemasForStudy() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();

        List<UploadSchema> schemaList = uploadSchemaService.getUploadSchemasForStudy(studyId);
        ResourceList<UploadSchema> schemaResourceList = new ResourceList<>(schemaList);
        return ok(UploadSchema.PUBLIC_SCHEMA_WRITER.writeValueAsString(schemaResourceList));
    }

    /**
     * Service handler for updating a new schema revision, using V4 API semantics. See
     * {@link org.sagebionetworks.bridge.dao.UploadSchemaDao#updateSchemaRevisionV4}
     *
     * @param schemaId
     *         schema ID to update
     * @param revision
     *         schema revision to update
     * @return Play result, with the updated schema
     */
    public Result updateSchemaRevisionV4(String schemaId, int revision) throws JsonProcessingException {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();

        UploadSchema uploadSchema = parseJson(request(), UploadSchema.class);
        UploadSchema updatedSchema = uploadSchemaService.updateSchemaRevisionV4(studyId, schemaId, revision,
                uploadSchema);
        return ok(UploadSchema.PUBLIC_SCHEMA_WRITER.writeValueAsString(updatedSchema));
    }
}
