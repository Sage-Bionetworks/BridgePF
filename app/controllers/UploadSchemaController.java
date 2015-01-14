package controllers;

import org.springframework.beans.factory.annotation.Autowired;
import play.mvc.Result;

import org.sagebionetworks.bridge.models.studies.Study;
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
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        UploadSchema uploadSchema = parseJson(request(), UploadSchema.class);
        UploadSchema createdSchema = uploadSchemaService.createOrUpdateUploadSchema(study, uploadSchema);
        return okResult(createdSchema);
    }

    /**
     * Play controller for GET /researcher/v1/uploadSchema/:schemaId. This API fetches the upload schema with the
     * specified ID. If there is more than one revision of the schema, this fetches the latest revision. If the schema
     * doesn't exist, this API throws a 404 exception.
     *
     * @param schemaId
     *         schema ID to fetch
     * @return Play result with the fetched schema in JSON format
     */
    public Result getUploadSchema(String schemaId) {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        UploadSchema uploadSchema = uploadSchemaService.getUploadSchema(study, schemaId);
        return okResult(uploadSchema);
    }
}
