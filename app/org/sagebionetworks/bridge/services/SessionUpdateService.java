package org.sagebionetworks.bridge.services;

import java.util.LinkedHashSet;
import java.util.Map;

import org.joda.time.DateTimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

/**
 * Service that updates the state of a user's session, eventually its dependencies as well (the state of the 
 * user's push notification topic subscriptions, and the session as it is cached). Changes that update a 
 * user's session should go through this service to ensure dependencies are handled correctly.
 */
@Component
public class SessionUpdateService {
    
    private CacheProvider cacheProvider;
    
    private ConsentService consentService;
    
    @Autowired
    public final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }
    @Autowired
    public final void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }
    
    public void updateTimeZone(UserSession session, DateTimeZone timeZone) {
        session.setParticipant(builder(session).withTimeZone(timeZone).build());
        cacheProvider.setUserSession(session);
    }
    
    public void updateLanguage(UserSession session, LinkedHashSet<String> languages) {
        session.setParticipant(builder(session).withLanguages(languages).build());
        cacheProvider.setUserSession(session);
    }
    
    public void updateExternalId(UserSession session, ExternalIdentifier externalId) {
        session.setParticipant(builder(session).withExternalId(externalId.getIdentifier()).build());
        cacheProvider.setUserSession(session);
    }
    
    public void updateParticipant(UserSession session, CriteriaContext context, StudyParticipant participant) {
        session.setParticipant(participant);
        
        Map<SubpopulationGuid,ConsentStatus> statuses = consentService.getConsentStatuses(context);
        session.setConsentStatuses(statuses);
        
        cacheProvider.setUserSession(session);
    }
    
    public void updateDataGroups(UserSession session, CriteriaContext context) {
        session.setParticipant(builder(session).withDataGroups(context.getUserDataGroups()).build());
        
        Map<SubpopulationGuid,ConsentStatus> statuses = consentService.getConsentStatuses(context);
        session.setConsentStatuses(statuses);
                
        cacheProvider.setUserSession(session);
    }
    
    public void updateSharingScope(UserSession session, SharingScope sharingScope) {
        session.setParticipant(builder(session).withSharingScope(sharingScope).build());
        
        cacheProvider.setUserSession(session);
    }
    
    public void updateConsentStatus(UserSession session, CriteriaContext context, SharingScope sharingScope) {
        Map<SubpopulationGuid, ConsentStatus> statuses = consentService.getConsentStatuses(context);
        session.setConsentStatuses(statuses);
        
        StudyParticipant.Builder builder = builder(session);
        builder.withSharingScope(sharingScope);
        if (!session.doesConsent()) {
            builder.withSharingScope(SharingScope.NO_SHARING);
        }
        session.setParticipant(builder.build());
        
        cacheProvider.setUserSession(session);
    }
    
    private StudyParticipant.Builder builder(UserSession session) {
        return new StudyParticipant.Builder().copyOf(session.getParticipant());
    }
}
