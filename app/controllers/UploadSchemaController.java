package controllers;

import org.springframework.beans.factory.annotation.Autowired;
import play.mvc.Result;

import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.UploadSchemaService;

public class UploadSchemaController extends BaseController {
    private UploadSchemaService uploadSchemaService;

    @Autowired
    public void setUploadSchemaService(UploadSchemaService uploadSchemaService) {
        this.uploadSchemaService = uploadSchemaService;
    }

    public Result createOrUpdateUploadSchema(String schemaId) throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        UploadSchema uploadSchema = parseJson(request(), UploadSchema.class);
        UploadSchema createdSchema = uploadSchemaService.createOrUpdateUploadSchema(study, schemaId, uploadSchema);
        return okResult(createdSchema);
    }

    public Result getUploadSchema(String schemaId) throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        UploadSchema uploadSchema = uploadSchemaService.getUploadSchema(study, schemaId);
        return okResult(uploadSchema);
    }
}
