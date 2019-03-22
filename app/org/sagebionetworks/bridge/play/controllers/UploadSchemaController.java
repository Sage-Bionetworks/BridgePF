package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.WORKER;

import java.io.IOException;
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
        return createdResult(UploadSchema.PUBLIC_SCHEMA_WRITER, createdSchema);
    }

    /**
     * Play controller for POST /researcher/v1/uploadSchema/:schemaId. This API creates an upload schema, using the
     * study for the current service endpoint and the schema of the specified schema. If the schema already exists,
     * this method updates it instead.
     *
     * @return Play result, with the created or updated schema in JSON format
     */
    public Result createOrUpdateUploadSchema() throws JsonProcessingException, IOException {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        UploadSchema uploadSchema = parseJson(request(), UploadSchema.class);
        UploadSchema createdSchema = uploadSchemaService.createOrUpdateUploadSchema(studyId, uploadSchema);
        return okResult(UploadSchema.PUBLIC_SCHEMA_WRITER, createdSchema);
    }

    public Result deleteAllRevisionsOfUploadSchema(String schemaId, String physical) {
        UserSession session = getAuthenticatedSession(DEVELOPER, ADMIN);
        
        if ("true".equals(physical) && session.isInRole(ADMIN)) {
            uploadSchemaService.deleteUploadSchemaByIdPermanently(session.getStudyIdentifier(), schemaId);
        } else {
            uploadSchemaService.deleteUploadSchemaById(session.getStudyIdentifier(), schemaId);    
        }
        return okResult("Schemas have been deleted.");
    }
    
    public Result deleteSchemaRevision(String schemaId, int revision, String physical) {
        UserSession session = getAuthenticatedSession(DEVELOPER, ADMIN);
        
        if ("true".equals(physical) && session.isInRole(ADMIN)) {
            uploadSchemaService.deleteUploadSchemaByIdAndRevisionPermanently(session.getStudyIdentifier(), schemaId, revision);
        } else {
            uploadSchemaService.deleteUploadSchemaByIdAndRevision(session.getStudyIdentifier(), schemaId, revision);
        }
        return okResult("Schema revision has been deleted.");
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
    public Result getUploadSchema(String schemaId) throws JsonProcessingException, IOException {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        UploadSchema uploadSchema = uploadSchemaService.getUploadSchema(studyId, schemaId);
        return okResult(UploadSchema.PUBLIC_SCHEMA_WRITER, uploadSchema);
    }
    
    /**
     * Play controller for GET /v3/uploadschemas/:schemaId. Returns all revisions of this upload schema. If the 
     * schema doesn't exist, this API throws a 404 exception.
     * @param schemaId
     *         schema ID to fetch
     * @param includeDeleted
     *         "true" if logically deleted items should be included in results, they are excluded otherwise
     * @return Play result with an array of all revisions of the fetched schema in JSON format
     */
    public Result getUploadSchemaAllRevisions(String schemaId, String includeDeleted)
            throws JsonProcessingException, IOException {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        List<UploadSchema> uploadSchemas = uploadSchemaService.getUploadSchemaAllRevisions(studyId, schemaId,
                Boolean.valueOf(includeDeleted));
        ResourceList<UploadSchema> uploadSchemaResourceList = new ResourceList<>(uploadSchemas);
        return okResult(UploadSchema.PUBLIC_SCHEMA_WRITER, uploadSchemaResourceList);
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
    public Result getUploadSchemaByIdAndRev(String schemaId, int rev) throws JsonProcessingException, IOException {
        UserSession session = getAuthenticatedSession(DEVELOPER, WORKER);
        StudyIdentifier studyId = session.getStudyIdentifier();

        UploadSchema uploadSchema = uploadSchemaService.getUploadSchemaByIdAndRev(studyId, schemaId, rev);
        return okResult(UploadSchema.PUBLIC_SCHEMA_WRITER, uploadSchema);
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
    public Result getUploadSchemasForStudy(String includeDeleted) throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER);
        StudyIdentifier studyId = session.getStudyIdentifier();

        List<UploadSchema> schemaList = uploadSchemaService.getUploadSchemasForStudy(studyId, Boolean.valueOf(includeDeleted));
        ResourceList<UploadSchema> schemaResourceList = new ResourceList<>(schemaList);
        return okResult(UploadSchema.PUBLIC_SCHEMA_WRITER, schemaResourceList);
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
    public Result updateSchemaRevisionV4(String schemaId, int revision) throws JsonProcessingException, IOException {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();

        UploadSchema uploadSchema = parseJson(request(), UploadSchema.class);
        UploadSchema updatedSchema = uploadSchemaService.updateSchemaRevisionV4(studyId, schemaId, revision,
                uploadSchema);
        return okResult(UploadSchema.PUBLIC_SCHEMA_WRITER, updatedSchema);
    }
}
