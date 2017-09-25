package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;

import java.util.List;

import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.services.AppConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

@Controller
public class AppConfigController extends BaseController {

    private AppConfigService appConfigService;
    
    @Autowired
    final void setAppConfigService(AppConfigService appConfigService) {
        this.appConfigService = appConfigService;
    }
    
    public Result getSelfAppConfig() {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        CriteriaContext context = getCriteriaContext(session);
        
        AppConfig appConfig = appConfigService.getAppConfigForUser(context);
        
        return okResult(appConfig);
    }
    
    public Result getAppConfigs() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        List<AppConfig> results = appConfigService.getAppConfigs(session.getStudyIdentifier());
        
        return okResult(results);
    }
    
    public Result getAppConfig(String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        AppConfig appConfig = appConfigService.getAppConfig(session.getStudyIdentifier(), guid);
        
        return okResult(appConfig);
    }
    
    public Result createAppConfig() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        AppConfig appConfig = parseJson(request(), AppConfig.class);
        
        AppConfig created = appConfigService.createAppConfig(session.getStudyIdentifier(), appConfig);
        
        return createdResult(new GuidVersionHolder(created.getGuid(), created.getVersion()));
    }
    
    public Result updateAppConfig(String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        AppConfig appConfig = parseJson(request(), AppConfig.class);
        appConfig.setGuid(guid);
        
        AppConfig updated = appConfigService.updateAppConfig(session.getStudyIdentifier(), appConfig);
        
        return okResult(new GuidVersionHolder(updated.getGuid(), updated.getVersion()));
    }
    
    public Result deleteAppConfig(String guid) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        appConfigService.deleteAppConfig(session.getStudyIdentifier(), guid);
        
        return okResult("App config deleted.");
    }
}
