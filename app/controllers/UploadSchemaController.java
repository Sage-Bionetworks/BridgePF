package controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.UploadSchemaService;

/** Play controller for the /researcher/v1/uploadSchema APIs */
@Controller("uploadSchemaController")
public class UploadSchemaController extends BaseController {
    private UploadSchemaService uploadSchemaService;

    /** Service handler for Upload Schema APIs. This is configured by Spring. */
    @Autowired
    public void setUploadSchemaService(UploadSchemaService uploadSchemaService) {
        this.uploadSchemaService = uploadSchemaService;
    }

    /**
     * Play controller for POST /researcher/v1/uploadSchema/:schemaId. This API creates an upload schema, using the
     * study for the current service endpoint and the schema of the specified schema. If the schema already exists,
     * this method updates it instead.
     *
     * @return Play result, with the created or updated schema in JSON format
     */
    public Result createOrUpdateUploadSchema() {
        UserSession session = getAuthenticatedResearcherSession();
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        UploadSchema uploadSchema = parseJson(request(), UploadSchema.class);
        UploadSchema createdSchema = uploadSchemaService.createOrUpdateUploadSchema(studyId, uploadSchema);
        return okResult(createdSchema);
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
        UserSession session = getAuthenticatedResearcherSession();
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
        UserSession session = getAuthenticatedResearcherSession();
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
    public Result getUploadSchema(String schemaId) {
        UserSession session = getAuthenticatedResearcherSession();
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        UploadSchema uploadSchema = uploadSchemaService.getUploadSchema(studyId, schemaId);
        return okResult(uploadSchema);
    }

    /**
     * Play controller for GET /researcher/v1/uploadSchema/forStudy. This API fetches all revisions of all upload
     * schemas in the current study. This is generally used by worker apps to validate uploads against schemas.
     * @return Play result with list of schemas for this study
     */
    public Result getUploadSchemasForStudy() throws Exception {
        UserSession session = getAuthenticatedResearcherSession();
        StudyIdentifier studyId = session.getStudyIdentifier();

        List<UploadSchema> schemaList = uploadSchemaService.getUploadSchemasForStudy(studyId);
        return okResult(schemaList);
    }
}
