package org.sagebionetworks.bridge.services;

import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.itp.IntentToParticipate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.validators.IntentToParticipateValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IntentService {

    /** Hold on to the intent for 4 hours. */
    private static final int EXPIRATION_IN_SECONDS = 4 * 60 * 60;
    
    private StudyService studyService;
    
    private SubpopulationService subpopService;
    
    private ConsentService consentService;
    
    private CacheProvider cacheProvider;
    
    private NotificationsService notificationsService;
    
    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    @Autowired
    final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    }
    
    @Autowired
    final void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }
    
    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }
    
    @Autowired
    final void setNotificationsService(NotificationsService notificationsService) {
        this.notificationsService = notificationsService;
    }
    
    public void submitIntentToParticipate(IntentToParticipate intent) {
        Validate.entityThrowingException(IntentToParticipateValidator.INSTANCE, intent);
        
        // validate the references are real
        Study study = studyService.getStudy(intent.getStudy());
        if (study.getInstallLinks().isEmpty()) {
            throw new BadRequestException("Study not configured to receive intent to participate.");
        }
        SubpopulationGuid guid = SubpopulationGuid.create(intent.getSubpopGuid());
        subpopService.getSubpopulation(study, guid);
        
        // It's validated and pointing to real things. Persist it.
        String cacheKey = getCacheKey(study, guid, intent.getPhone());
        cacheProvider.setObject(cacheKey, intent, EXPIRATION_IN_SECONDS);
        
        // send an app store link to download the app.
        String message = getInstallLink(intent.getOsName(), study.getInstallLinks());
        notificationsService.sendSMSMessage(study, intent.getPhone(), message);
    }
    
    public void registerIntentToParticipate(Study study, StudyParticipant participant) {
        Phone phone = participant.getPhone();
        // Somehow, this is being called but the user has no phone number.
        if (phone == null) {
            return;
        }
        
        List<Subpopulation> subpops = subpopService.getSubpopulations(study.getStudyIdentifier());
        for (Subpopulation subpop : subpops) {
            String cacheKey = getCacheKey(study, subpop.getGuid(), phone);
            IntentToParticipate intent = cacheProvider.getObject(cacheKey, IntentToParticipate.class);
            if (intent != null) {
                cacheProvider.removeObject(cacheKey);
                consentService.consentToResearch(study, subpop.getGuid(), participant, 
                        intent.getConsentSignature(), intent.getScope(), true);
            }
        }
    }
    
    private String getCacheKey(StudyIdentifier studyId, SubpopulationGuid subpopGuid, Phone phone) {
        return subpopGuid.getGuid() + ":" + phone.getNumber() + ":" + studyId.getIdentifier() + ":itp";
    }

    protected String getInstallLink(String osName, Map<String,String> installLinks) {
        String message = installLinks.get(osName);
        // OS name wasn't submitted or it's wrong, use the universal link
        if (message == null) {
            message = installLinks.get(OperatingSystem.UNIVERSAL);
        }
        // Don't have a link named "Universal" so just find ANYTHING
        if (message == null && !installLinks.isEmpty()) {
            message = installLinks.values().iterator().next();
        }
        return message;
    }
}
