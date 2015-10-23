package org.sagebionetworks.bridge.validators;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import com.google.common.base.Strings;

@Component
public class ConsentSignatureValidator implements Validator {
    
    private static final String CANNOT_BE_BLANK = "cannot be missing, null, or blank";
    private static final String CANNOT_BE_EMPTY_STRING = "cannot be an empty string";
    
    @Override
    public boolean supports(Class<?> clazz) {
        return ConsentSignature.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        ConsentSignature sig = (ConsentSignature) target;
        if (Strings.isNullOrEmpty(sig.getName())) {
            errors.rejectValue("name", CANNOT_BE_BLANK);
        }
        if (Strings.isNullOrEmpty(sig.getBirthdate())) {
            errors.rejectValue("birthdate", CANNOT_BE_BLANK);
        }
        // Just verify that the signature timestamp is close to the current time...
        long oneHourAgo = DateTime.now().minusHours(1).getMillis();
        if (sig.getSignedOn() <= oneHourAgo) {
            errors.rejectValue("signedOn", "must be a valid signature timestamp");
        }
        // Signature image is currently optional. Some studies may collect a signature, but some may not. It's okay
        // to let the client validate this until we're sure this 100% required for all consents.
        String imageData = sig.getImageData();
        String imageMimeType = sig.getImageMimeType();
        if (imageData != null && imageData.isEmpty()) {
            errors.rejectValue("imageData", CANNOT_BE_EMPTY_STRING);
        }
        if (imageMimeType != null && imageMimeType.isEmpty()) {
            errors.rejectValue("imageMimeType", CANNOT_BE_EMPTY_STRING);
        }

        // if one of them is not null, but not both
        if (imageData != null ^ imageMimeType != null) {
            errors.reject("If you specify one of imageData or imageMimeType, you must specify both");
        }
    }
}
