package org.sagebionetworks.bridge.services.backfill;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.BackfillRecord;
import org.sagebionetworks.bridge.models.BackfillTask;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UploadCertificateService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Backfills CMS certificates for studies.
 */
public class StudyCertificateBackfill extends AsyncBackfillTemplate {

    private static final ObjectMapper MAPPER = BridgeObjectMapper.get();

    private StudyService studyService;
    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    private UploadCertificateService uploadCertService;
    public void setUploadCertificateService(UploadCertificateService uploadCertService) {
        this.uploadCertService = uploadCertService;
    }

    @Override
    int getLockExpireInSeconds() {
        return 30 * 60;
    }

    @Override
    void doBackfill(final BackfillTask task, BackfillCallback callback) {
        List<Study> studies = studyService.getStudies();
        for (final Study study : studies) {
            uploadCertService.createCmsKeyPair(study.getIdentifier());
            callback.newRecords(new BackfillRecord() {
                @Override
                public String getTaskId() {
                    return task.getId();
                }
                @Override
                public long getTimestamp() {
                    return DateTime.now(DateTimeZone.UTC).getMillis();
                }
                @Override
                public String getRecord() {
                    ObjectNode node = MAPPER.createObjectNode();
                    node.put("studyIdentifier", study.getIdentifier());
                    node.put("operation", "Key pair created");
                    try {
                        return MAPPER.writeValueAsString(node);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }
}
