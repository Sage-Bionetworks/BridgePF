package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.visualization.MpowerVisualization;

/** Validator for mPower visualization. */
public class MpowerVisualizationValidator implements Validator {
    /** Singleton instance of this validator. */
    public static final MpowerVisualizationValidator INSTANCE = new MpowerVisualizationValidator();

    /** {@inheritDoc} */
    @Override
    public boolean supports(Class<?> clazz) {
        return MpowerVisualization.class.isAssignableFrom(clazz);
    }

    /**
     * Simple validation. It the visualization object must have a date, must have a health code, and must have
     * visualization data. To allow rapid development, we don't validate deeply into the visualization data, only that
     * it exists
     *
     * @see org.springframework.validation.Validator#validate
     */
    @Override
    public void validate(Object target, Errors errors) {
        if (target == null) {
            errors.rejectValue("visualization", "cannot be null");
        } else if (!(target instanceof MpowerVisualization)) {
            errors.rejectValue("visualization", "is the wrong type");
        } else {
            MpowerVisualization viz = (MpowerVisualization) target;

            // date
            if (viz.getDate() == null) {
                errors.rejectValue("date", "must be specified");
            }

            // health code
            if (StringUtils.isBlank(viz.getHealthCode())) {
                errors.rejectValue("healthCode", "must be specified");
            }

            // visualization
            if (viz.getVisualization() == null || viz.getVisualization().isNull()) {
                errors.rejectValue("visualization", "must be specified");
            }
        }
    }
}
