package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.cache.CacheProvider;
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
    
    public void submitIntentToParticipate(IntentToParticipate itp) {
        Validate.entityThrowingException(IntentToParticipateValidator.INSTANCE, itp);
        
        // validate the references are real
        Study study = studyService.getStudy(itp.getStudy());
        SubpopulationGuid guid = SubpopulationGuid.create(itp.getSubpopGuid());
        subpopService.getSubpopulation(study, guid);
        
        // It's validated and pointing to real things. Persist it.
        String cacheKey = getCacheKey(study, guid, itp.getEmail(), itp.getPhone());
        cacheProvider.setObject(cacheKey, itp, EXPIRATION_IN_SECONDS);
    }
    
    public void registerIntentToParticipate(Study study, StudyParticipant participant) {
        String email = participant.getEmail();
        String phone = null; // TBD: participant.getPhone();
        
        List<Subpopulation> subpops = subpopService.getSubpopulations(study.getStudyIdentifier());
        for (Subpopulation subpop : subpops) {
            String cacheKey = getCacheKey(study, subpop.getGuid(), email, phone);
            IntentToParticipate itp = cacheProvider.getObject(cacheKey, IntentToParticipate.class);
            if (itp != null) {
                cacheProvider.removeObject(cacheKey);
                consentService.consentToResearch(study, subpop.getGuid(), participant, 
                        itp.getConsentSignature(), itp.getScope(), true);
            }
        }
    }
    
    private String getCacheKey(StudyIdentifier studyId, SubpopulationGuid subpopGuid, String email, String phone) {
        String identifier = (email != null) ? email : phone;
        return subpopGuid.getGuid() + ":" + identifier + ":" + studyId.getIdentifier() + ":itp";
    }
    
}
