package org.sagebionetworks.bridge.services;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.redis.RedisKey;
import org.sagebionetworks.bridge.util.BridgeCollectors;

import com.google.common.collect.Sets;

/**
 * Manages a count of the enrolled participants in a study. Although calculated for a study, it involves 
 * both consents and the subpopulations associated with a study, so it is its own service. It als caches 
 * of the enrollment number because it is potentially expensive to calculate. 
 */
@Component
public class StudyEnrollmentService {
    
    private static final int TWENTY_FOUR_HOURS = (24*60*60);

    private JedisOps jedisOps;
    private UserConsentDao userConsentDao;
    private SubpopulationService subpopService;
    
    @Autowired
    final void setStringOps(JedisOps jedisOps) {
        this.jedisOps = jedisOps;
    }
    @Autowired
    final void setUserConsentDao(UserConsentDao userConsentDao) {
        this.userConsentDao = userConsentDao;
    }
    @Autowired
    final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    }
    
    /**
     * The number of participants are calculated as the set of all unique health codes across all 
     * subpopulations in the study. This value is not cached and it is expensive to perform; to 
     * check if enrollment is reached, use isStudyAtEnrollmentLimit().
     * @param studyIdentifier
     * @return
     * @see isStudyAtEnrollmentLimit
     */
    public long getNumberOfParticipants(StudyIdentifier studyIdentifier) {
        Set<String> healthCodes = Sets.newHashSet();
        
        List<SubpopulationGuid> guids = subpopService.getSubpopulations(studyIdentifier).stream()
                .map(Subpopulation::getGuid).collect(BridgeCollectors.toImmutableList());
        
        for (SubpopulationGuid guid : guids) {
            Set<String> subpopCodes = userConsentDao.getParticipantHealthCodes(guid);
            healthCodes.addAll(subpopCodes);
        }
        
        return healthCodes.size();
    }

    /**
     * Has enrollment met or slightly exceeded the limit set for th study? This value is cached.
     * @param study
     * @return
     */
    public boolean isStudyAtEnrollmentLimit(Study study) {
        if (study.getMaxNumOfParticipants() == 0) {
            return false;
        }
        String key = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey(study.getIdentifier());
        long count = Long.MAX_VALUE;
        
        // getNumberOfParticipants() is very expensive. Cache this.
        String countString = jedisOps.get(key);
        if (countString == null) {
            count = getNumberOfParticipants(study); 
            jedisOps.setex(key, TWENTY_FOUR_HOURS, Long.toString(count));
        } else {
            count = Long.parseLong(countString);
        }
        return (count >= study.getMaxNumOfParticipants());
    }
    
    /**
     * Increment the study enrollment if this user has signed their first consent in the study. 
     * This adjusts the cached value without recalculating the enrollment from scratch (an 
     * expensive operation).
     * @param study
     * @param user
     */
    public void incrementStudyEnrollment(Study study, User user) {
        if (study.getMaxNumOfParticipants() == 0) {
            return;
        }
        if (ConsentStatus.hasOnlyOneSignedConsent(user.getConsentStatuses())) {
            String key = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey(study.getIdentifier());
            jedisOps.incr(key);
        }
    }
    
    /**
     * Decrement the study enrollment if this user has withdrawn from their last consent to 
     * participate in the study. This adjusts the cached value without recalculating the 
     * enrollment from scratch (an expensive operation).
     * @param study
     * @param user
     */
    public void decrementStudyEnrollment(Study study, User user) {
        if (study.getMaxNumOfParticipants() == 0) {
            return;
        }
        if (!user.doesConsent()) {
            String key = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey(study.getIdentifier());
            String count = jedisOps.get(key);
            if (count != null && Long.parseLong(count) > 0) {
                jedisOps.decr(key);
            }
        }
    }

}
