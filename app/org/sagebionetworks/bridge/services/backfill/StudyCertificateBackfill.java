package org.sagebionetworks.bridge.services.backfill;

import java.util.List;

import org.sagebionetworks.bridge.models.Backfill;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UploadCertificateService;

/**
 * Backfills CMS certificates for studies.
 */
public class StudyCertificateBackfill implements BackfillService {

    private StudyService studyService;
    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    private UploadCertificateService uploadCertService;
    public void setUploadCertificateService(UploadCertificateService uploadCertService) {
        this.uploadCertService = uploadCertService;
    }

    @Override
    public Backfill backfill() {
        List<Study> studies = studyService.getStudies();
        for (Study study : studies) {
            uploadCertService.createCmsKeyPair(study.getIdentifier());
        }
        Backfill backfill = new Backfill("studyCertificateBackfill");
        backfill.setCompleted(true);
        backfill.setCount(studies.size());
        return backfill;
    }
}
