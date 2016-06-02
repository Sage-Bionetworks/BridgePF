package org.sagebionetworks.bridge.models.reports;

import static org.sagebionetworks.bridge.models.reports.ReportDataType.PARTICIPANT;
import static org.sagebionetworks.bridge.models.reports.ReportDataType.STUDY;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.validators.ReportDataKeyValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A key object for reports, which come in two types: participant and study. Participant 
 * reports can include reports for every participant in a study, while study reports will
 * produce one report for an entire study (each at one or more LocalDates).
 */
public final class ReportDataKey implements BridgeEntity {
    
    private static final ReportDataKeyValidator VALIDATOR = new ReportDataKeyValidator();
    
    private final String studyId;
    private final String identifier;
    private final String healthCode;
    private final ReportDataType reportType;
    
    private ReportDataKey(String healthCode, String identifier, StudyIdentifier studyId, ReportDataType type) {
        this.healthCode = healthCode;
        this.identifier = identifier;
        this.studyId = studyId.getIdentifier();
        this.reportType = type;
    }
    
    @JsonIgnore
    public String getStudyId() {
        return studyId;
    }

    public String getIdentifier() {
        return identifier;
    }

    @JsonIgnore
    public String getHealthCode() {
        return healthCode;
    }

    public ReportDataType getReportType() {
        return reportType;
    }
    
    @Override
    public String toString() {
        return (healthCode != null) ?
                String.format("%s:%s:%s", healthCode, identifier, studyId) :
                String.format("%s:%s", identifier, studyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(healthCode, identifier, studyId, reportType);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ReportDataKey other = (ReportDataKey) obj;
        return Objects.equals(healthCode, other.healthCode) && Objects.equals(identifier, other.identifier)
                && Objects.equals(studyId, other.studyId) && Objects.equals(reportType, other.reportType);
    }
    
    public static class Builder {
        private StudyIdentifier studyId;
        private String identifier;
        private String healthCode;
        
        public Builder withStudyIdentifier(StudyIdentifier studyId) {
            this.studyId = studyId;
            return this;
        }
        public Builder withIdentifier(String identifier) {
            this.identifier = identifier;
            return this;
        }
        public Builder withHealthCode(String healthCode) {
            this.healthCode = healthCode;
            return this;
        }
        public ReportDataKey build() {
            ReportDataType type = (StringUtils.isBlank(healthCode)) ? STUDY : PARTICIPANT;
            ReportDataKey key = new ReportDataKey(healthCode, identifier, studyId, type);
            Validate.entityThrowingException(VALIDATOR, key);
            return key;
        }
    }
    
}
