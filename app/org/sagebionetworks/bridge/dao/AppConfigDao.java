package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface AppConfigDao {
    
    /**
     * Get all the app configuration objects for the study.
     */
    List<AppConfig> getAppConfigs(StudyIdentifier studyId, boolean includeDeleted);
    
    /**
     * Get a specific app configuration object for this study.
     */
    AppConfig getAppConfig(StudyIdentifier studyId, String guid);
    
    /**
     * Create an app configuration object. If the object already exists, 
     * a copy will be created.
     */
    AppConfig createAppConfig(AppConfig appConfig);
    
    /**
     * Update an existing app config.
     */
    AppConfig updateAppConfig(AppConfig appConfig);
    
    /**
     * Delete an individual app config by marking it as deleted. The record 
     * will not be returned from the APIs but it is still in the database.
     */
    void deleteAppConfig(StudyIdentifier studyId, String guid);
    
    /**
     * Permanently delete an individual app config. The record is deleted 
     * from the database. 
     */
    void deleteAppConfigPermanently(StudyIdentifier studyId, String guid);
    
}
