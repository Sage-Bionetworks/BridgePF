package org.sagebionetworks.bridge.services.backfill;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.SubpopulationService;

@Component("consentCreatedOn")
public class ConsentCreatedOnBackfill extends AsyncBackfillTemplate {
    private StudyService studyService;
    private SubpopulationService subpopulationService;
    private StudyConsentDao studyConsentDao;
    
    @Autowired
    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    @Autowired
    public void setSubpopulationService(SubpopulationService subpopulationService) {
        this.subpopulationService = subpopulationService;
    }
    @Autowired
    public void setStudyConsentDao(StudyConsentDao studyConsentDao) {
        this.studyConsentDao = studyConsentDao;
    }

    @Override
    int getLockExpireInSeconds() {
        return 30 * 60;
    }
    
    private List<Study> getStudies() {
        return studyService.getStudies();
    }

    @Override
    void doBackfill(final BackfillTask task, BackfillCallback callback) {
        List<Study> studies = getStudies();
        for (Study study : studies) {
            callback.newRecords(getBackfillRecordFactory().createOnly(task, "Examining study " + study.getIdentifier() + "..."));
            
            List<Subpopulation> subpopulations = subpopulationService.getSubpopulations(study.getStudyIdentifier());
            for (Subpopulation subpopulation : subpopulations) {
                
                StudyConsent consent = studyConsentDao.getActiveConsent(subpopulation.getGuid());
                subpopulation.setActiveConsentCreatedOn(consent.getCreatedOn());
                
                subpopulationService.updateSubpopulation(study, subpopulation);
            }
        }
    }

}
