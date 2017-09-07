package org.sagebionetworks.bridge.models.reports;

import java.util.Objects;

import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A key object for reports, which come in two types: participant and study. Participant 
 * reports can include reports for every participant in a study, while study reports will
 * produce one report for an entire study (each at one or more LocalDates). All values are 
 * required except healthCode, which is only required if the report type is PARTICIPANT.
 */
public final class ReportDataKey implements BridgeEntity {
    
    private final StudyIdentifier studyId;
    private final String identifier;
    private final String healthCode;
    private final ReportType reportType;
    
    private ReportDataKey(String healthCode, String identifier, StudyIdentifier studyId, ReportType reportType) {
        this.studyId = studyId;
        this.identifier = identifier;
        this.healthCode = healthCode;
        this.reportType = reportType;
    }
    
    @JsonIgnore
    public StudyIdentifier getStudyId() {
        return studyId;
    }

    public String getIdentifier() {
        return identifier;
    }

    @JsonIgnore
    public String getHealthCode() {
        return healthCode;
    }

    public ReportType getReportType() {
        return reportType;
    }
    
    @JsonIgnore
    public String getKeyString() {
        return (healthCode != null) ?
                String.format("%s:%s:%s", healthCode, identifier, studyId.getIdentifier()) :
                String.format("%s:%s", identifier, studyId.getIdentifier());
    }
    
    @JsonIgnore
    public String getIndexKeyString() {
        return String.format("%s:%s",studyId.getIdentifier(), reportType.name());
    }
    
    @Override
    public String toString() {
        return "ReportDataKey [studyId=" + studyId + ", identifier=" + identifier
                + ", healthCode=[REDACTED], reportType=" + reportType + "]";
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
        private ReportType reportType;
        
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
        public Builder withReportType(ReportType reportType) {
            this.reportType = reportType;
            return this;
        }
        public ReportDataKey build() {
            return new ReportDataKey(healthCode, identifier, studyId, reportType);
        }
    }
    
}
