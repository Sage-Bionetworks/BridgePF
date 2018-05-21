package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;

import org.apache.commons.codec.binary.Base64;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class UploadValidator implements Validator {

    private static final long MAX_UPLOAD_SIZE = 50L * 1000L * 1000L; // 50 MB

    @Override
    public boolean supports(Class<?> clazz) {
        return UploadRequest.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        UploadRequest uploadRequest = (UploadRequest)object;
        
        final String name = uploadRequest.getName();
        if (name == null || name.isEmpty()) {
            errors.rejectValue("name", CANNOT_BE_BLANK);
        }
        final String contentType = uploadRequest.getContentType();
        if (contentType == null || contentType.isEmpty()) {
            errors.rejectValue("contentType", CANNOT_BE_BLANK);
        }
        final long length = uploadRequest.getContentLength();
        if (length <= 0L) {
            errors.rejectValue("contentLength", "Invalid content length. Must be > 0.");
        }
        if (length > MAX_UPLOAD_SIZE) {
            errors.rejectValue("contentLength", "Content length is above the allowed maximum.");   
        }
        final String base64md5 = uploadRequest.getContentMd5();
        if (base64md5 == null || base64md5.isEmpty()) {
            errors.rejectValue("contentMd5", "MD5 must not be empty.");
        }
        try {
            Base64.decodeBase64(base64md5.getBytes());
        } catch (Exception e) {
            errors.rejectValue("contentMd5", "MD5 is not base64 encoded.");
        }
    }

}
