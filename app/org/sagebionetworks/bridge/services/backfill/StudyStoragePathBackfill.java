package org.sagebionetworks.bridge.services.backfill;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyConsentForm;
import org.sagebionetworks.bridge.models.studies.StudyConsentView;
import org.sagebionetworks.bridge.services.StudyConsentService;
import org.sagebionetworks.bridge.services.StudyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import com.google.common.io.CharStreams;

@Component("studyStorageBackfill")
public class StudyStoragePathBackfill extends AsyncBackfillTemplate {

    private StudyService studyService;
    private StudyConsentDao studyConsentDao;
    private StudyConsentService studyConsentService;

    @Autowired
    public void setStudyConsentDao(StudyConsentDao studyConsentDao) {
        this.studyConsentDao = studyConsentDao;
    }
    @Autowired
    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    @Autowired
    public void setStudyConsentService(StudyConsentService studyConsentService) {
        this.studyConsentService = studyConsentService;
    }
    
    @Override
    int getLockExpireInSeconds() {
        return 30 * 60;
    }
    
    @Override
    void doBackfill(BackfillTask task, BackfillCallback callback) {
        for (Study study : studyService.getStudies()) {
            try {
                StudyConsent consent = studyConsentDao.getConsent(study);
                if (consent != null && consent.getPath() != null && consent.getStoragePath() == null) {
                    String content = getFilesystemContent(consent);
                    StudyConsentForm form = new StudyConsentForm(content);
                    StudyConsentView view = studyConsentService.addConsent(study, form);
                    studyConsentService.activateConsent(study, view.getStudyConsent().getCreatedOn());
                    
                    callback.newRecords(getBackfillRecordFactory().createOnly(task, "Study '"+study.getIdentifier()+"' updated."));
                } else {
                    callback.newRecords(getBackfillRecordFactory().createOnly(task, "Study '"+study.getIdentifier()+"' skipped (has S3 content)."));
                }
            } catch(Throwable t) {
                callback.newRecords(getBackfillRecordFactory().createOnly(task, "Study '"+study.getIdentifier()+"' could not be updated: " + t.getMessage()));
            }
        }
    }
    
    private String getFilesystemContent(StudyConsent consent) throws Exception {
        final FileSystemResource resource = new FileSystemResource(consent.getPath());
        try (InputStream is = resource.getInputStream();
             InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);) {
            return CharStreams.toString(isr);
        }                
    }
}