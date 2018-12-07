package org.sagebionetworks.bridge;

import java.util.Set;

import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.google.common.collect.ImmutableSet;

public class RequestContext {
    
    public static final RequestContext NULL_INSTANCE = new RequestContext.Builder().build();

    private final String id;
    private final StudyIdentifier callerStudyId;
    private final Set<String> callerSubstudies;
    private final Set<Roles> callerRoles;
    
    private RequestContext(String id, String callerStudyId, Set<String> callerSubstudies, Set<Roles> callerRoles) {
        this.id = id;
        this.callerStudyId = (callerStudyId == null) ? null : new StudyIdentifierImpl(callerStudyId);
        this.callerSubstudies = callerSubstudies;
        this.callerRoles = callerRoles;
    }
    
    public String getId() {
        return id;
    }
    public String getCallerStudyId() {
        return (callerStudyId == null) ? null : callerStudyId.getIdentifier();
    }
    public StudyIdentifier getCallerStudyIdentifier() {
        return callerStudyId;
    }
    public Set<String> getCallerSubstudies() {
        return callerSubstudies;
    }
    public Set<Roles> getCallerRoles() {
        return callerRoles;
    }
    
    public static class Builder {
        private String callerStudyId;
        private Set<String> callerSubstudies;
        private Set<Roles> callerRoles;
        private String id;

        public Builder withCallerStudyId(StudyIdentifier studyId) {
            this.callerStudyId = (studyId == null) ? null : studyId.getIdentifier();
            return this;
        }
        public Builder withCallerSubstudies(Set<String> callerSubstudies) {
            this.callerSubstudies = (callerSubstudies == null) ? null : ImmutableSet.copyOf(callerSubstudies);
            return this;
        }
        public Builder withCallerRoles(Set<Roles> roles) {
            this.callerRoles = (roles == null) ? null : ImmutableSet.copyOf(roles);
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
            if (callerRoles == null) {
                callerRoles = ImmutableSet.of();
            }
            return new RequestContext(id, callerStudyId, callerSubstudies, callerRoles);
        }
    }

    @Override
    public String toString() {
        return "RequestContext [callerStudyId=" + callerStudyId + ", callerSubstudies=" + 
                callerSubstudies + ", callerRoles=" + callerRoles + ", id=" + id + "]";
    }
}
