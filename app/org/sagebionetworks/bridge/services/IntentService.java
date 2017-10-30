package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.cache.CacheProvider;
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
    
    public void submitIntentToParticipate(IntentToParticipate intent) {
        Validate.entityThrowingException(IntentToParticipateValidator.INSTANCE, intent);
        
        // validate the references are real
        Study study = studyService.getStudy(intent.getStudy());
        SubpopulationGuid guid = SubpopulationGuid.create(intent.getSubpopGuid());
        subpopService.getSubpopulation(study, guid);
        
        // It's validated and pointing to real things. Persist it.
        String cacheKey = getCacheKey(study, guid, intent.getEmail(), intent.getPhone());
        cacheProvider.setObject(cacheKey, intent, EXPIRATION_IN_SECONDS);
    }
    
    public void registerIntentToParticipate(Study study, StudyParticipant participant) {
        String email = participant.getEmail();
        Phone phone = participant.getPhone();
        
        List<Subpopulation> subpops = subpopService.getSubpopulations(study.getStudyIdentifier());
        for (Subpopulation subpop : subpops) {
            // We do not know whether the consent was saved using an email or a phone number, so look for both.
            String cacheKey = getCacheKey(study, subpop.getGuid(), email, null);
            IntentToParticipate intent = cacheProvider.getObject(cacheKey, IntentToParticipate.class);
            if (intent == null) {
                cacheKey = getCacheKey(study, subpop.getGuid(), null, phone);
                intent = cacheProvider.getObject(cacheKey, IntentToParticipate.class);
            }
            if (intent != null) {
                cacheProvider.removeObject(cacheKey);
                consentService.consentToResearch(study, subpop.getGuid(), participant, 
                        intent.getConsentSignature(), intent.getScope(), true);
            }
        }
    }
    
    private String getCacheKey(StudyIdentifier studyId, SubpopulationGuid subpopGuid, String email, Phone phone) {
        String identifier = email;
        if (identifier == null && phone != null) {
            identifier = phone.getNumber();
        }
        return subpopGuid.getGuid() + ":" + identifier + ":" + studyId.getIdentifier() + ":itp";
    }
    
}
