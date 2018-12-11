package org.sagebionetworks.bridge.validators;

import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.SubstudyService;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class ExternalIdValidator implements Validator {

    private static final String IDENTIFIER_PATTERN = "^[a-zA-Z0-9_-]+$";
    
    private SubstudyService substudyService;
    private boolean isV3;
    
    public ExternalIdValidator(SubstudyService substudyService, boolean isV3) {
        this.substudyService = substudyService;
        this.isV3 = isV3;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return ExternalIdentifier.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        ExternalIdentifier extId = (ExternalIdentifier)object;

        String callerStudyId = BridgeUtils.getRequestContext().getCallerStudyId();
        Set<String> callerSubstudies = BridgeUtils.getRequestContext().getCallerSubstudies();
        
        if (StringUtils.isBlank(extId.getIdentifier())) {
            errors.rejectValue("identifier", "cannot be null or blank");
        } else if (!extId.getIdentifier().matches(IDENTIFIER_PATTERN)) {
            String msg = String.format("'%s' must contain only digits, letters, underscores and dashes", extId.getIdentifier());
            errors.rejectValue("identifier", msg);
        }
        if (!isV3) {
            StudyIdentifier studyId = StringUtils.isBlank(extId.getStudyId()) ? 
                    null : new StudyIdentifierImpl(extId.getStudyId());
            if (StringUtils.isBlank(extId.getSubstudyId())) {
                errors.rejectValue("substudyId", "cannot be null or blank");
            } else if (substudyService.getSubstudy(studyId, extId.getSubstudyId(), false) == null) {
                errors.rejectValue("substudyId", "is not a valid substudy");
            } else if (!callerSubstudies.isEmpty() && !callerSubstudies.contains(extId.getSubstudyId())) {
                errors.rejectValue("substudyId", "is not a valid substudy");
            }
        }
        if (StringUtils.isBlank(extId.getStudyId())) {
            errors.rejectValue("studyId", "cannot be null or blank");
        } else if (!extId.getStudyId().equals(callerStudyId)) {
            errors.rejectValue("studyId", "is not a valid study");
        }
    }
}
