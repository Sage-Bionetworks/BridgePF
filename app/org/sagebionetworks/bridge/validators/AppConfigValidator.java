package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Set;

import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.services.SurveyService;
import org.sagebionetworks.bridge.services.UploadSchemaService;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class AppConfigValidator implements Validator {

    private SurveyService surveyService;
    private UploadSchemaService schemaService;
    private boolean isNew;
    private Set<String> dataGroups;
    
    public AppConfigValidator(SurveyService surveyService, UploadSchemaService schemaService, Set<String> dataGroups,
            boolean isNew) {
        this.surveyService = surveyService;
        this.schemaService = schemaService;
        this.dataGroups = dataGroups;
        this.isNew = isNew;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return AppConfig.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        AppConfig appConfig = (AppConfig)object;
        
        if (!isNew && isBlank(appConfig.getGuid())) {
            errors.rejectValue("guid", "is required");
        }
        if (isBlank(appConfig.getLabel())) {
            errors.rejectValue("label", "is required");
        }
        if (appConfig.getCriteria() == null) {
            errors.rejectValue("criteria", "are required");
        } else {
            CriteriaUtils.validate(appConfig.getCriteria(), dataGroups, errors);    
        }
        if (isBlank(appConfig.getStudyId())) {
            errors.rejectValue("studyId", "is required");
        } else {
            // We can't validate schema references if there is no studyId
            if (appConfig.getSchemaReferences() != null) {
                StudyIdentifier studyId = new StudyIdentifierImpl(appConfig.getStudyId());
                
                for (int i=0; i < appConfig.getSchemaReferences().size(); i++) {
                    SchemaReference ref = appConfig.getSchemaReferences().get(i);
                    errors.pushNestedPath("schemaReferences["+i+"]");
                    if (ref.getRevision() == null) {
                        errors.rejectValue("revision", "is required");
                    } else {
                        try {
                            schemaService.getUploadSchemaByIdAndRev(studyId, ref.getId(), ref.getRevision());    
                        } catch(EntityNotFoundException e) {
                            errors.rejectValue("", "does not refer to an upload schema");
                        }
                    }
                    errors.popNestedPath();
                }
            }
        }
        if (appConfig.getSurveyReferences() != null) {
            
            for (int i=0; i < appConfig.getSurveyReferences().size(); i++) {
                SurveyReference ref = appConfig.getSurveyReferences().get(i);
                errors.pushNestedPath("surveyReferences["+i+"]");
                if (ref.getCreatedOn() == null) {
                    errors.rejectValue("createdOn", "is required");
                } else {
                    try {
                        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(ref);
                        Survey survey = surveyService.getSurvey(keys, false);
                        if (!survey.isPublished()) {
                            errors.rejectValue("", "has not been published");
                        }
                    } catch(EntityNotFoundException e) {
                        errors.rejectValue("", "does not refer to a survey");
                    }
                }
                errors.popNestedPath();
            }
        }
    }

}
