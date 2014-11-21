package org.sagebionetworks.bridge.validators;

import com.google.common.base.Strings;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.studies.ConsentSignature;

public class ConsentSignatureValidator implements Validator {
    @Override
    public boolean supports(Class<?> clazz) {
        return ConsentSignature.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        ConsentSignature sig = (ConsentSignature) target;
        if (Strings.isNullOrEmpty(sig.getName())) {
            errors.rejectValue("name", Validate.CANNOT_BE_BLANK);
        }
        if (Strings.isNullOrEmpty(sig.getBirthdate())) {
            errors.rejectValue("birthdate", Validate.CANNOT_BE_BLANK);
        }

        // Signature image is currently optional. Some studies may collect a signature, but some may not. It's okay
        // to let the client validate this until we're sure this 100% required for all consents.
        String imageData = sig.getImageData();
        String imageMimeType = sig.getImageMimeType();
        if (imageData != null && imageData.isEmpty()) {
            errors.rejectValue("imageData", Validate.CANNOT_BE_EMPTY_STRING);
        }
        if (imageMimeType != null && imageMimeType.isEmpty()) {
            errors.rejectValue("imageMimeType", Validate.CANNOT_BE_EMPTY_STRING);
        }

        // if one of them is not null, but not both
        if (imageData != null ^ imageMimeType != null) {
            errors.reject("If you specify one of imageData or imageMimeType, you must specify both");
        }
    }
}
