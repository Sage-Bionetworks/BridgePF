package org.sagebionetworks.bridge.validators;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Strings;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadSchema;

/** Validator for {@link org.sagebionetworks.bridge.models.upload.UploadSchema} */
public class UploadSchemaValidator implements Validator {
    /** Singleton instance of this validator. */
    public static final UploadSchemaValidator INSTANCE = new UploadSchemaValidator();

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
     *     <li>name is null or empty</li>
     *     <li>revision is negative</li>
     *     <li>schemaId is null or empty</li>
     *     <li>schemaType is null</li>
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

            // name
            if (Strings.isNullOrEmpty(uploadSchema.getName())) {
                errors.rejectValue("name", "is required");
            }

            // revision must be non-negative. (0 is allowed if it's a new schema. revisions 1 and above are saved
            // schemas)
            if (uploadSchema.getRevision() < 0) {
                errors.rejectValue("revision", "must be equal to or greater than zero");
            }

            // schema ID
            if (Strings.isNullOrEmpty(uploadSchema.getSchemaId())) {
                errors.rejectValue("schemaId", "is required");
            }

            // schema type
            if (uploadSchema.getSchemaType() == null) {
                errors.rejectValue("schemaType", "is required");
            }
            
            // fieldDefinitions
            List<UploadFieldDefinition> fieldDefList = uploadSchema.getFieldDefinitions();
            if (fieldDefList == null || fieldDefList.isEmpty()) {
                errors.rejectValue("fieldDefinitions", "requires at least one definition");
            } else {
                Set<String> fieldNameSet = new HashSet<>();

                for (int i=0; i < fieldDefList.size(); i++) {
                    UploadFieldDefinition fieldDef = fieldDefList.get(i);
                    String fieldDefinitionKey = "fieldDefinitions["+i+"]";
                    if (fieldDef == null) {
                        errors.rejectValue(fieldDefinitionKey, "is required");
                    } else {
                        errors.pushNestedPath(fieldDefinitionKey);

                        String fieldName = fieldDef.getName();
                        if (Strings.isNullOrEmpty(fieldName)) {
                            errors.rejectValue("name", "is required");
                        } else {
                            if (fieldNameSet.contains(fieldName)) {
                                errors.rejectValue("name", "cannot use " + fieldName + " (used by another field)");
                            }
                            fieldNameSet.add(fieldName);
                        }

                        //noinspection ConstantConditions
                        if (fieldDef.getType() == null) {
                            errors.rejectValue("type", "is required");
                        }

                        errors.popNestedPath();
                    }
                    
                }
            }
        }
    }
}
