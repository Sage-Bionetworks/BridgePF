package org.sagebionetworks.bridge.migration;

import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.springframework.context.support.AbstractApplicationContext;

class HealthDataStudyFilter implements HealthDataFilter {

    private final String studyId;

    HealthDataStudyFilter(final AbstractApplicationContext appContext, final String studyId) {
        Utils.checkStudy(appContext, studyId);
        this.studyId = studyId;
    }

    @Override
    public boolean keep(HealthDataRecord record) {
        if (record != null && this.studyId.equals(record.getStudyId())) {
            return true;
        }
        return false;
    }
}
