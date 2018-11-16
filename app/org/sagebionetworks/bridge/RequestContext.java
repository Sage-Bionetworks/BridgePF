package org.sagebionetworks.bridge;

import java.util.Set;

import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.google.common.collect.ImmutableSet;

public class RequestContext {
    
    public static final RequestContext NULL_INSTANCE = new RequestContext.Builder().build();

    final String id;
    final String callerStudyId;
    final Set<String> callerSubstudies;
    
    private RequestContext(String id, String callerStudyId, Set<String> callerSubstudies) {
        this.id = id;
        this.callerStudyId = callerStudyId;
        this.callerSubstudies = callerSubstudies;
    }
    
    public String getId() {
        return id;
    }
    public String getCallerStudyId() {
        return callerStudyId;
    }
    public Set<String> getCallerSubstudies() {
        return callerSubstudies;
    }
    
    public static class Builder {
        private String callerStudyId;
        private Set<String> callerSubstudies;
        private String id;

        public Builder withCallerStudyId(StudyIdentifier studyId) {
            this.callerStudyId = (studyId == null) ? null : studyId.getIdentifier();
            return this;
        }
        public Builder withCallerSubstudies(Set<String> callerSubstudies) {
            this.callerSubstudies = (callerSubstudies == null) ? null : ImmutableSet.copyOf(callerSubstudies);
            return this;
        }
        public Builder withRequestId(String id) {
            this.id = id;
            return this;
        }
        
        public RequestContext build() {
            if (callerSubstudies == null) {
                callerSubstudies = ImmutableSet.of();
            }
            return new RequestContext(id, callerStudyId, callerSubstudies);
        }
    }

    @Override
    public String toString() {
        return "RequestContext [callerStudyId=" + callerStudyId + ", callerSubstudies=" + 
                callerSubstudies + ", id=" + id + "]";
    }
}
