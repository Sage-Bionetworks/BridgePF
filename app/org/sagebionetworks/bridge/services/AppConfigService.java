package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableList;

import java.util.Comparator;
import java.util.List;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.AppConfigDao;
import org.sagebionetworks.bridge.dynamodb.DynamoAppConfig;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
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
    
    private UploadSchemaService schemaService;

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
    final void setSchemaService(UploadSchemaService schemaService) {
        this.schemaService = schemaService;
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
    
    public AppConfig getAppConfigForUser(CriteriaContext context) {
        checkNotNull(context);

        List<AppConfig> appConfigs = getAppConfigs(context.getStudyIdentifier());

        List<AppConfig> matches = appConfigs.stream().filter(oneAppConfig -> {
            return CriteriaUtils.matchCriteria(context, oneAppConfig.getCriteria());
        }).sorted(Comparator.comparingLong(AppConfig::getCreatedOn))
          .collect(toImmutableList());

        // Should have matched one and only one app config.
        // The goal of the following code is not to introduce production exceptions when changing app configs.
        if (matches.isEmpty()) {
            // If there are no matches, return the "default" app config
            return getDefaultAppConfig(context.getStudyIdentifier(), context.getClientInfo());
        } else if (matches.size() != 1) {
            // If there is more than one match, return the one created first, but log an error
            LOG.error("CriteriaContext matches more than one app config: criteriaContext=" + context + ", appConfigs="+matches);
            return matches.get(0);
        }
        return matches.get(0);
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
    
    /**
     * If no app config has been defined by the study designer, return an app config
     * that selects the most recently published versions of all schemas and surveys
     * in the study.
     */
    public AppConfig getDefaultAppConfig(final StudyIdentifier studyId, final ClientInfo clientInfo) {
        long timestamp = getCurrentTimestamp();
        AppConfig appConfig = AppConfig.create();
        appConfig.setLabel(studyId.getIdentifier() + " default app config");
        appConfig.setGuid(studyId.getIdentifier());
        appConfig.setStudyId(studyId.getIdentifier());
        appConfig.setCreatedOn(timestamp);
        appConfig.setModifiedOn(timestamp);
        
        List<SurveyReference> publishedSurveyReferences = surveyService.getAllSurveysMostRecentlyPublishedVersion(studyId)
                .stream().map((survey) -> {
                    return new SurveyReference(survey.getIdentifier(), survey.getGuid(), new DateTime(survey.getCreatedOn()));
        }).collect(toImmutableList());

        appConfig.setSurveyReferences(publishedSurveyReferences);
        
        List<SchemaReference> schemaReferences = schemaService.getUploadSchemasForStudy(studyId).stream().map((schema) -> {
                    return schemaService.getLatestUploadSchemaRevisionForAppVersion(studyId, schema.getSchemaId(), clientInfo);
        }).map((schema) -> {
            return new SchemaReference(schema.getSchemaId(), schema.getRevision());
        }).collect(toImmutableList());
        
        appConfig.setSchemaReferences(schemaReferences);
        
        return appConfig;
    }
    
}
