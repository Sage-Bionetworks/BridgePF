package controllers;

import org.sagebionetworks.bridge.models.UploadRequest;
import org.sagebionetworks.bridge.models.UploadSession;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.services.UploadService;

import play.mvc.Result;

public class UploadController extends BaseController {

    private UploadService uploadService;

    public void setUploadService(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    public Result upload() throws Exception {
        UserSession session = getAuthenticatedSession();
        UploadRequest uploadRequest = UploadRequest.fromJson(requestToJSON(request()));
        UploadSession uploadSession = uploadService.createUpload(session.getUser(), uploadRequest);
        return ok(constructJSON(uploadSession));
    }

    public Result uploadComplete(String uploadId) throws Exception {
        uploadService.uploadComplete(uploadId);
        return ok("Upload " + uploadId + " complete!");
    }
}
