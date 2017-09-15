package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;

import org.sagebionetworks.bridge.dao.AppConfigDao;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.util.BridgeCollectors;
import org.sagebionetworks.bridge.validators.AppConfigValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Validator;

@Component
public class AppConfigService {

    private AppConfigDao appConfigDao;
    
    private StudyService studyService;
    
    @Autowired
    final void setAppConfigDao(AppConfigDao appConfigDao) {
        this.appConfigDao = appConfigDao;
    }
    
    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    public List<AppConfig> getAppConfigs(StudyIdentifier studyId) {
        checkNotNull(studyId);
        
        return appConfigDao.getAppConfigs(studyId);
    }
    
    public AppConfig getAppConfig(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkArgument(isNotBlank(guid));
        
        return appConfigDao.getAppConfig(studyId, guid);
    }
    
    public AppConfig getAppConfigForUser(CriteriaContext context) {
        checkNotNull(context);

        List<AppConfig> appConfigs = getAppConfigs(context.getStudyIdentifier());

        // return one or null. 
        List<AppConfig> matches = appConfigs.stream().filter(oneAppConfig -> {
            return CriteriaUtils.matchCriteria(context, oneAppConfig.getCriteria());
        }).collect(BridgeCollectors.toImmutableList());
        
        if (matches.isEmpty()) {
            throw new EntityNotFoundException(AppConfig.class);
        } else if (matches.size() != 1) {
            throw new ConstraintViolationException.Builder()
                .withMessage("App request matches multiple configurations").build();
        }
        return matches.get(0);
    }
    
    public AppConfig createAppConfig(StudyIdentifier studyId, AppConfig appConfig) {
        checkNotNull(studyId);
        checkNotNull(appConfig);
        
        appConfig.setStudyId(studyId.getIdentifier());
        
        Study study = studyService.getStudy(studyId);
        Validator validator = new AppConfigValidator(study.getDataGroups());
        Validate.entityThrowingException(validator, appConfig);
        
        return appConfigDao.createAppConfig(appConfig);
    }
    
    public AppConfig updateAppConfig(StudyIdentifier studyId, AppConfig appConfig) {
        checkNotNull(studyId);
        checkNotNull(appConfig);
        
        appConfig.setStudyId(studyId.getIdentifier());
        
        Study study = studyService.getStudy(studyId);
        Validator validator = new AppConfigValidator(study.getDataGroups());
        Validate.entityThrowingException(validator, appConfig);
        
        return appConfigDao.updateAppConfig(appConfig);
    }
    
    public void deleteAppConfig(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkArgument(isNotBlank(guid));
        
        appConfigDao.deleteAppConfig(studyId, guid);
    }
    
    public void deleteAllAppConfigs(StudyIdentifier studyId) {
        checkNotNull(studyId);
        
        appConfigDao.deleteAllAppConfigs(studyId);
    }
    
}
