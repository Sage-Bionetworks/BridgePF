package controllers;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpStatus;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
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
            throw new BridgeServiceException("File name must not be empty.", HttpStatus.SC_BAD_REQUEST);
        }
        final String contentType = uploadRequest.getContentType();
        if (contentType == null || contentType.isEmpty()) {
            throw new BridgeServiceException("Content type must not be empty.", HttpStatus.SC_BAD_REQUEST);
        }
        final long length = uploadRequest.getContentLength();
        if (length <= 0L) {
            throw new BridgeServiceException("Invalid content length. Must be > 0.", HttpStatus.SC_BAD_REQUEST);
        }
        if (length > MAX_UPLOAD_SIZE) {
            throw new BridgeServiceException("Content length is above the allowed maximum.", HttpStatus.SC_BAD_REQUEST);
        }
        final String base64md5 = uploadRequest.getContentMd5();
        if (base64md5 == null || base64md5.isEmpty()) {
            throw new BridgeServiceException("MD5 must not be empty.", HttpStatus.SC_BAD_REQUEST);
        }
        try {
            Base64.decodeBase64(base64md5);
        } catch (Exception e) {
            throw new BridgeServiceException("MD5 is not base64 encoded.", HttpStatus.SC_BAD_REQUEST);
        }
    }
}
