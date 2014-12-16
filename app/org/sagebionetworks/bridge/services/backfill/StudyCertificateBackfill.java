package org.sagebionetworks.bridge.services.backfill;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.models.BackfillRecord;
import org.sagebionetworks.bridge.models.BackfillStatus;
import org.sagebionetworks.bridge.models.BackfillTask;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UploadCertificateService;

/**
 * Backfills CMS certificates for studies.
 */
public class StudyCertificateBackfill extends AsyncBackfillTemplate {

    private StudyService studyService;
    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    private UploadCertificateService uploadCertService;
    public void setUploadCertificateService(UploadCertificateService uploadCertService) {
        this.uploadCertService = uploadCertService;
    }

    @Override
    void doBackfill(final String user, final String name, BackfillCallback callback) {
        final String taskId = UUID.randomUUID().toString();
        callback.start(new BackfillTask() {
            @Override
            public String getId() {
                return taskId;
            }
            @Override
            public long getTimestamp() {
                return DateTime.now(DateTimeZone.UTC).getMillis();
            }
            @Override
            public String getName() {
                return name;
            }
            @Override
            public String getDescription() {
                return "Backfills key pairs for studies.";
            }
            @Override
            public String getUser() {
                return user;
            }
            @Override
            public String getStatus() {
                return BackfillStatus.SUBMITTED.name();
            }
        });
        List<Study> studies = studyService.getStudies();
        for (final Study study : studies) {
            uploadCertService.createCmsKeyPair(study.getIdentifier());
            callback.newRecords(new BackfillRecord() {
                @Override
                public String getTaskId() {
                    return taskId;
                }
                @Override
                public long getTimestamp() {
                    return DateTime.now(DateTimeZone.UTC).getMillis();
                }
                @Override
                public String getRecord() {
                    return "{\"studyIdentifier\": " + study.getIdentifier();
                }
            });
        }
        callback.done();
    }

    @Override
    int getExpireInSeconds() {
        return 30 * 60;
    }
}
