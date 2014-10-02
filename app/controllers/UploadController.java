package controllers;

import org.apache.commons.codec.binary.Base64;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.UploadRequest;
import org.sagebionetworks.bridge.models.UploadSession;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.services.UploadService;

import play.mvc.Result;

public class UploadController extends BaseController {

    private static final long MAX_UPLOAD_SIZE = 10L * 1000L * 1000L; // 10 MB

    private UploadService uploadService;

    public void setUploadService(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    public Result upload() throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        UploadRequest uploadRequest = UploadRequest.fromJson(requestToJSON(request()));
        validateRequest(uploadRequest);
        UploadSession uploadSession = uploadService.createUpload(session.getUser(), uploadRequest);
        return ok(constructJSON(uploadSession));
    }

    public Result uploadComplete(String uploadId) throws Exception {
        getAuthenticatedAndConsentedSession();
        uploadService.uploadComplete(uploadId);
        return ok("Upload " + uploadId + " complete!");
    }

    void validateRequest(UploadRequest uploadRequest) {
        final String name = uploadRequest.getName();
        if (name == null || name.isEmpty()) {
            throw new BadRequestException("File name must not be empty.");
        }
        final String contentType = uploadRequest.getContentType();
        if (contentType == null || contentType.isEmpty()) {
            throw new BadRequestException("Content type must not be empty.");
        }
        final long length = uploadRequest.getContentLength();
        if (length <= 0L) {
            throw new BadRequestException("Invalid content length. Must be > 0.");
        }
        if (length > MAX_UPLOAD_SIZE) {
            throw new BadRequestException("Content length is above the allowed maximum.");
        }
        final String base64md5 = uploadRequest.getContentMd5();
        if (base64md5 == null || base64md5.isEmpty()) {
            throw new BadRequestException("MD5 must not be empty.");
        }
        try {
            Base64.decodeBase64(base64md5);
        } catch (Exception e) {
            throw new BadRequestException("MD5 is not base64 encoded.");
        }
    }
}
