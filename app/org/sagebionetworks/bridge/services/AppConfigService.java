package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.AppConfigDao;
import org.sagebionetworks.bridge.dynamodb.DynamoAppConfig;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.util.BridgeCollectors;
import org.sagebionetworks.bridge.validators.AppConfigValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Validator;

@Component
public class AppConfigService {
    private static final Logger LOG = LoggerFactory.getLogger(AppConfigService.class);
    
    private AppConfigDao appConfigDao;
    
    private StudyService studyService;
    
    private SurveyService surveyService;
    
    @Autowired
    final void setAppConfigDao(AppConfigDao appConfigDao) {
        this.appConfigDao = appConfigDao;
    }
    
    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    @Autowired
    final void setSurveyService(SurveyService surveyService) {
        this.surveyService = surveyService;
    }   
    
    // In order to mock this value;
    protected long getCurrentTimestamp() {
        return DateUtils.getCurrentMillisFromEpoch(); 
    }
    
    // In order to mock this value;
    protected String getGUID() {
        return BridgeUtils.generateGuid();
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
    
    public AppConfig getAppConfigForUser(CriteriaContext context, boolean throwException) {
        checkNotNull(context);

        List<AppConfig> appConfigs = getAppConfigs(context.getStudyIdentifier());

        List<AppConfig> matches = appConfigs.stream().filter(oneAppConfig -> {
            return CriteriaUtils.matchCriteria(context, oneAppConfig.getCriteria());
        }).sorted(Comparator.comparingLong(AppConfig::getCreatedOn))
          .collect(BridgeCollectors.toImmutableList());

        // Should have matched one and only one app config.
        if (matches.isEmpty()) {
            if (throwException) {
                throw new EntityNotFoundException(AppConfig.class);    
            } else {
                return null;
            }
        } else if (matches.size() != 1) {
            // If there is more than one match, return the one created first, but log an error
            LOG.error("CriteriaContext matches more than one app config: criteriaContext=" + context + ", appConfigs="+matches);
        }
        AppConfig matched = matches.get(0);
        // Resolve survey references to pick up survey identifiers
        matched.setSurveyReferences(matched.getSurveyReferences().stream()
            .map(surveyReference -> resolveSurvey(surveyReference)).collect(Collectors.toList()));
        return matched;
    }

    // Survey and schema references in an app config are "hard" references... they must reference a 
    // version or createdOn timestamp of a version and this has been validated when creating/
    // updating the reference. We're only concerned with adding survey identifier here.
    SurveyReference resolveSurvey(SurveyReference surveyRef) {
        try {
            GuidCreatedOnVersionHolder surveyKeys = new GuidCreatedOnVersionHolderImpl(surveyRef);
            Survey survey = surveyService.getSurvey(surveyKeys, false);
            return new SurveyReference(survey.getIdentifier(), survey.getGuid(), new DateTime(survey.getCreatedOn()));
        } catch(EntityNotFoundException e) {
            // Survey references can be bogus, and should be return unmodified if they don't reference an existing survey
            return surveyRef;
        }
    }
    
    public AppConfig createAppConfig(StudyIdentifier studyId, AppConfig appConfig) {
        checkNotNull(studyId);
        checkNotNull(appConfig);
        
        appConfig.setStudyId(studyId.getIdentifier());
        
        Study study = studyService.getStudy(studyId);
        Validator validator = new AppConfigValidator(study.getDataGroups(), true);
        Validate.entityThrowingException(validator, appConfig);

        long timestamp = getCurrentTimestamp();

        DynamoAppConfig newAppConfig = new DynamoAppConfig();
        newAppConfig.setLabel(appConfig.getLabel());
        newAppConfig.setStudyId(appConfig.getStudyId());
        newAppConfig.setCriteria(appConfig.getCriteria());
        newAppConfig.setClientData(appConfig.getClientData());
        newAppConfig.setSurveyReferences(appConfig.getSurveyReferences());
        newAppConfig.setSchemaReferences(appConfig.getSchemaReferences());
        newAppConfig.setCreatedOn(timestamp);
        newAppConfig.setModifiedOn(timestamp);
        newAppConfig.setGuid(getGUID());
        
        appConfigDao.createAppConfig(newAppConfig);
        newAppConfig.setVersion(newAppConfig.getVersion());
        return newAppConfig;
    }
    
    public AppConfig updateAppConfig(StudyIdentifier studyId, AppConfig appConfig) {
        checkNotNull(studyId);
        checkNotNull(appConfig);
        
        appConfig.setStudyId(studyId.getIdentifier());
        
        Study study = studyService.getStudy(studyId);
        Validator validator = new AppConfigValidator(study.getDataGroups(), false);
        Validate.entityThrowingException(validator, appConfig);
        
        // Throw a 404 if the GUID is not valid.
        AppConfig persistedConfig = appConfigDao.getAppConfig(studyId, appConfig.getGuid());
        appConfig.setCreatedOn(persistedConfig.getCreatedOn());
        appConfig.setModifiedOn(getCurrentTimestamp());
        
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
