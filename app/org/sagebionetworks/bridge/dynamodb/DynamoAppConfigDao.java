package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.AppConfigDao;
import org.sagebionetworks.bridge.dao.CriteriaDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.google.common.collect.Lists;

@Component
public class DynamoAppConfigDao implements AppConfigDao {

    private DynamoDBMapper mapper;
    private CriteriaDao criteriaDao;
    
    @Resource(name = "appConfigDdbMapper")
    final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Autowired
    final void setCriteriaDao(CriteriaDao criteriaDao) {
        this.criteriaDao = criteriaDao;
    }
    
    public List<AppConfig> getAppConfigs(StudyIdentifier studyId) {
        DynamoAppConfig key = new DynamoAppConfig();
        key.setStudyId(studyId.getIdentifier());

        DynamoDBQueryExpression<DynamoAppConfig> query = new DynamoDBQueryExpression<DynamoAppConfig>()
                .withHashKeyValues(key);
        
        PaginatedQueryList<DynamoAppConfig> results = mapper.query(DynamoAppConfig.class, query);
        
        List<AppConfig> list = Lists.newArrayListWithCapacity(results.size());
        for (DynamoAppConfig appConfig : results) {
            loadCriteria(appConfig);
            list.add(appConfig);
        }
        return list;
    }
    
    public AppConfig getAppConfig(StudyIdentifier studyId, String guid) {
        DynamoAppConfig key = new DynamoAppConfig();
        key.setStudyId(studyId.getIdentifier());
        key.setGuid(guid);
        
        DynamoAppConfig appConfig = mapper.load(key);
        if (appConfig == null) {
            throw new EntityNotFoundException(AppConfig.class);
        }
        loadCriteria(appConfig);
        
        return appConfig;
    }
    
    public AppConfig createAppConfig(AppConfig appConfig) {
        checkNotNull(appConfig);
        
        DynamoAppConfig newAppConfig = new DynamoAppConfig();
        newAppConfig.setStudyId(appConfig.getStudyId());
        newAppConfig.setCriteria(appConfig.getCriteria());
        newAppConfig.setClientData(appConfig.getClientData());
        newAppConfig.setSurveyReferences(appConfig.getSurveyReferences());
        newAppConfig.setSchemaReferences(appConfig.getSchemaReferences());
        newAppConfig.setGuid(BridgeUtils.generateGuid());
        
        Criteria criteria = persistCriteria(newAppConfig);
        appConfig.setCriteria(criteria);
        
        mapper.save(newAppConfig);
        
        return newAppConfig;
    }
    
    public AppConfig updateAppConfig(AppConfig appConfig) {
        checkNotNull(appConfig);
        
        Criteria criteria = persistCriteria(appConfig);
        appConfig.setCriteria(criteria);
        
        mapper.save(appConfig);
        return appConfig;
    }
    
    public void deleteAppConfig(StudyIdentifier studyId, String guid) {
        AppConfig appConfig = getAppConfig(studyId, guid);
        mapper.delete(appConfig);
        criteriaDao.deleteCriteria(appConfig.getCriteria().getKey());
    }
    
    public void deleteAllAppConfigs(StudyIdentifier studyId) {
        List<AppConfig> appConfigs = getAppConfigs(studyId);
        if (!appConfigs.isEmpty()) {
            for (AppConfig oneAppConfig: appConfigs) {
                mapper.delete(oneAppConfig);
                criteriaDao.deleteCriteria(oneAppConfig.getCriteria().getKey());
            }
            List<FailedBatch> failures = mapper.batchDelete(appConfigs);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }
    
    private String getKey(AppConfig appConfig) {
        return "appconfig:" + appConfig.getGuid();
    }
    
    private Criteria persistCriteria(AppConfig config) {
        Criteria criteria = config.getCriteria();
        criteria.setKey(getKey(config));
        return criteriaDao.createOrUpdateCriteria(criteria);
    }

    private void loadCriteria(AppConfig config) {
        Criteria criteria = criteriaDao.getCriteria(getKey(config));
        if (criteria == null) {
            criteria = Criteria.create();
        }
        criteria.setKey(getKey(config));
        config.setCriteria(criteria);
    }
}
