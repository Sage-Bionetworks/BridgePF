package org.sagebionetworks.bridge.services;

import java.util.Map;

import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.StudyDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudyDao;
import org.sagebionetworks.bridge.models.studies.Study;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import com.google.common.collect.Maps;

@Component
public class EnterpriseStormpathStudyMigration implements ApplicationListener<ContextRefreshedEvent> {
    
    private static Logger logger = LoggerFactory.getLogger(EnterpriseStormpathStudyMigration.class);

    private Map<Environment, Map<String, String>> map = Maps.newHashMap();
    {
        Map<String, String> devMap = Maps.newHashMap();
        devMap.put("api", "https://enterprise.stormpath.io/v1/directories/7fxheMcEARjm7X2XPBufSM");
        devMap.put("asthma", "https://enterprise.stormpath.io/v1/directories/6KGMQRJ6sDD9uq9Y3ykbbO");
        devMap.put("diabetes", "https://enterprise.stormpath.io/v1/directories/74TDi7MfVeNUIu6WdyxhN");
        devMap.put("breastcancer", "https://enterprise.stormpath.io/v1/directories/4rz8FLlnVoZ6cGMwrc8X8U");
        devMap.put("parkinson", "https://enterprise.stormpath.io/v1/directories/4IXDCXpED2utTaG01kSlSP");
        devMap.put("cardiovascular", "https://enterprise.stormpath.io/v1/directories/3fSQrW63h6xUeFtINpByyD");
        map.put(Environment.DEV, devMap);
        
        Map<String, String> localMap = Maps.newHashMap();
        localMap.put("api", "https://enterprise.stormpath.io/v1/directories/7W9Lyy4h6PhtxVak4DlIWo");
        localMap.put("asthma", "https://enterprise.stormpath.io/v1/directories/6QrLNyN6ZCJfBoRMRDu3ms");
        localMap.put("diabetes", "https://enterprise.stormpath.io/v1/directories/4gctLBFlnNaUGBqhX81RGP");
        localMap.put("breastcancer", "https://enterprise.stormpath.io/v1/directories/4xIGs427YuEyAU0B8Pxr18");
        localMap.put("parkinson", "https://enterprise.stormpath.io/v1/directories/6MxAdNVQlABZKRyl9E7YJJ");
        localMap.put("cardiovascular", "https://enterprise.stormpath.io/v1/directories/2HFZCevSHzHs89dHeGRnif");
        map.put(Environment.LOCAL, localMap);
        
        Map<String, String> prodMap = Maps.newHashMap();
        prodMap.put("api", "https://enterprise.stormpath.io/v1/directories/4MFGMk7w7My1SvN0ciqlW");
        prodMap.put("asthma", "https://enterprise.stormpath.io/v1/directories/6dAmNV9tau70MpNPXPedrm");
        prodMap.put("diabetes", "https://enterprise.stormpath.io/v1/directories/sMCLLtCM8vlKWK08jXNcb");
        prodMap.put("breastcancer", "https://enterprise.stormpath.io/v1/directories/58w4lsjabqmMZJHd6krQpO");
        prodMap.put("parkinson", "https://enterprise.stormpath.io/v1/directories/gFAoPqsU5f8p637M6ChcT");
        prodMap.put("cardiovascular", "https://enterprise.stormpath.io/v1/directories/3KBfBRW2DLjcdvE0r5JuMn");
        map.put(Environment.PROD, prodMap);
       
        Map<String, String> uatMap = Maps.newHashMap();
        uatMap.put("api", "https://enterprise.stormpath.io/v1/directories/E1Wsr9r3k9dHVZcCCNmDa");
        uatMap.put("asthma", "https://enterprise.stormpath.io/v1/directories/6XoQsLP9HulVuYLyEEqwuQ");
        uatMap.put("diabetes", "https://enterprise.stormpath.io/v1/directories/2FF0UAjOpyeKKPn4KKOdUT");
        uatMap.put("breastcancer", "https://enterprise.stormpath.io/v1/directories/53uVeYNPcHpF0rsQB1aK94");
        uatMap.put("parkinson", "https://enterprise.stormpath.io/v1/directories/1dZ1XvoxWnLgJMVcnp0vMr");
        uatMap.put("cardiovascular", "https://enterprise.stormpath.io/v1/directories/4R76OdM8ePEjeVzbrGBqST");
        map.put(Environment.UAT, uatMap);
    }
    
    private StudyService studyService;
    
    private StudyDao studyDao;
    
    private CacheProvider cacheProvider;
    
    @Autowired
    public EnterpriseStormpathStudyMigration(StudyService studyService, DynamoStudyDao studyDao,
            CacheProvider cacheProvider) {
        this.studyService = studyService;
        this.studyDao = studyDao;
        this.cacheProvider = cacheProvider;
    }
    
    @Override
    public void onApplicationEvent(final ContextRefreshedEvent event) {
        Environment env = BridgeConfigFactory.getConfig().getEnvironment();
        for (Study study : studyService.getStudies()) {
            String studyId = study.getIdentifier();
            String enterpriseHref = map.get(env).get(studyId);
            try {
                if (enterpriseHref != null) {
                    if (!study.getStormpathHref().equals(enterpriseHref)) {
                        log("Migrating '%s' to HREF %s", studyId, enterpriseHref);
                        study.setStormpathHref(enterpriseHref);
                        
                        // Side-step the study service's rules preventing setting Stormpath HREF
                        studyDao.updateStudy(study);
                        cacheProvider.setStudy(study);
                    } else {
                        log("Study '%s' already migrated to %s", studyId, enterpriseHref);
                    }
                } else {
                    log("No enterprise HREF found for '%s'", studyId, "");
                }
            } catch(Throwable e) {
                e.printStackTrace();
            }
        }
    }
    
    private void log(String msg, String studyId, String enterpriseHref) {
        logger.info(String.format("MIGRATION: " + msg, studyId, enterpriseHref));
    }
}
