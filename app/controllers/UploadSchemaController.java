package controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import play.mvc.Result;

import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.UploadSchemaService;

/** Play controller for the /researcher/v1/uploadSchema APIs */
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
        UserSession session = getAuthenticatedResearcherOrAdminSession();
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        UploadSchema uploadSchema = parseJson(request(), UploadSchema.class);
        UploadSchema createdSchema = uploadSchemaService.createOrUpdateUploadSchema(studyId, uploadSchema);
        return okResult(createdSchema);
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
        UserSession session = getAuthenticatedResearcherOrAdminSession();
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
        // TODO: When we implement worker accounts, they should have access to the API as well.
        UserSession session = getAuthenticatedResearcherOrAdminSession();
        StudyIdentifier studyId = session.getStudyIdentifier();

        List<UploadSchema> schemaList = uploadSchemaService.getUploadSchemasForStudy(studyId);
        return okResult(schemaList);
    }
}
