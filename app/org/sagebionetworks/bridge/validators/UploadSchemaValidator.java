package org.sagebionetworks.bridge.validators;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.upload.UploadFieldSize;
import org.sagebionetworks.bridge.upload.UploadUtil;

/** Validator for {@link org.sagebionetworks.bridge.models.upload.UploadSchema} */
public class UploadSchemaValidator implements Validator {
    /** Singleton instance of this validator. */
    public static final UploadSchemaValidator INSTANCE = new UploadSchemaValidator();

    private static final int MAX_BYTES = 50000;
    private static final int MAX_COLUMNS = 100;

    /** {@inheritDoc} */
    @Override
    public boolean supports(Class<?> clazz) {
        return UploadSchema.class.isAssignableFrom(clazz);
    }

    /**
     * <p>
     * Validates the given object as a valid UploadSchema instance. This will flag errors in the following
     * conditions:
     *   <ul>
     *     <li>value is null or not an UploadSchema</li>
     *     <li>fieldDefinitions is null or empty</li>
     *     <li>fieldDefinitions contains null or invalid entries</li>
     *     <li>minAppVersion is greater than maxAppVersion</li>
     *     <li>name is blank</li>
     *     <li>revision is zero or negative</li>
     *     <li>schemaId is blank</li>
     *     <li>schemaType is null</li>
     *     <li>studyId is blank</li>
     *   </ul>
     * </p>
     *
     * @see org.springframework.validation.Validator#validate
     */
    @Override
    public void validate(Object target, Errors errors) {
        if (target == null) {
            errors.rejectValue("uploadSchema", "cannot be null");
        } else if (!(target instanceof UploadSchema)) {
            errors.rejectValue("uploadSchema", "is the wrong type");
        } else {
            UploadSchema uploadSchema = (UploadSchema) target;

            // min/maxAppVersion
            for (String osName : uploadSchema.getAppVersionOperatingSystems()) {
                Integer minAppVersion = uploadSchema.getMinAppVersion(osName);
                Integer maxAppVersion = uploadSchema.getMaxAppVersion(osName);
                if (minAppVersion != null && maxAppVersion != null && minAppVersion > maxAppVersion) {
                    errors.rejectValue("minAppVersions{" + osName + "}", "can't be greater than maxAppVersion");
                }
            }

            // name
            if (StringUtils.isBlank(uploadSchema.getName())) {
                errors.rejectValue("name", "is required");
            }

            // revision must be specified and positive
            if (uploadSchema.getRevision() <= 0) {
                errors.rejectValue("revision", "must be positive");
            }

            // schema ID
            if (StringUtils.isBlank(uploadSchema.getSchemaId())) {
                errors.rejectValue("schemaId", "is required");
            }

            // schema type
            if (uploadSchema.getSchemaType() == null) {
                errors.rejectValue("schemaType", "is required");
            }

            // study ID
            if (StringUtils.isBlank(uploadSchema.getStudyId())) {
                errors.rejectValue("studyId", "is required");
            }

            // fieldDefinitions
            List<UploadFieldDefinition> fieldDefList = uploadSchema.getFieldDefinitions();
            if (!fieldDefList.isEmpty()) {
                UploadFieldDefinitionListValidator.INSTANCE.validate(fieldDefList, errors, "fieldDefinitions");
            }

            // fieldDef max size
            UploadFieldSize fieldSize = UploadUtil.calculateFieldSize(fieldDefList);
            if (fieldSize.getNumBytes() > MAX_BYTES) {
                errors.rejectValue("fieldDefinitions",
                        "cannot be greater than 50000 bytes combined");
            }
            if (fieldSize.getNumColumns() > MAX_COLUMNS) {
                errors.rejectValue("fieldDefinitions",
                        "cannot be greater than 100 columns combined");
            }
        }
    }
}
