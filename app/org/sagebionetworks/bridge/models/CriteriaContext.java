package org.sagebionetworks.bridge.models;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.google.common.collect.ImmutableSet;

public final class CriteriaContext {
    
    private final StudyIdentifier studyId;
    private final String healthCode;
    private final ClientInfo clientInfo;
    private final Set<String> userDataGroups;
    // This set has ordered keys (most to least preferential)
    private final LinkedHashSet<String> languages;
    
    private CriteriaContext(StudyIdentifier studyId, String healthCode, ClientInfo clientInfo,
            Set<String> userDataGroups, LinkedHashSet<String> languages) {
        this.studyId = studyId;
        this.healthCode = healthCode;
        this.clientInfo = clientInfo;
        this.userDataGroups = (userDataGroups == null) ? ImmutableSet.of() : ImmutableSet.copyOf(userDataGroups);
        this.languages = (languages == null) ? new LinkedHashSet<>() : languages;
    }

    /**
    * Client information based on the supplied User-Agent header.
    * @return
    */
    public ClientInfo getClientInfo() {
        return clientInfo;
    }

    public Set<String> getUserDataGroups() {
        return userDataGroups;
    }
    
    public StudyIdentifier getStudyIdentifier() {
        return studyId;
    }
    
    public String getHealthCode() {
        return healthCode;
    }
    
    /**
     * Languages are sorted in order of descending preference of the LanguageRange objects 
     * submitted by the request via the Accept-Language HTTP header. That is to say, the 
     * client prefers the first language more than the second language, the second more than 
     * the third, etc. However, this isn't a SortedSet, because it is sorted by something 
     * (LanguageRange quality) that is external to the contents of the Set.
     */
    public LinkedHashSet<String> getLanguages() {
        return languages;
    }

    @Override
    public int hashCode() {
        return Objects.hash(studyId, healthCode, clientInfo, userDataGroups, languages);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        CriteriaContext other = (CriteriaContext)obj;
        return (Objects.equals(clientInfo, other.clientInfo) && 
                Objects.equals(userDataGroups, other.userDataGroups) && 
                Objects.equals(studyId, other.studyId) && 
                Objects.equals(healthCode, other.healthCode) && 
                Objects.equals(languages, other.languages));
    }

    @Override
    public String toString() {
        return "CriteriaContext [studyId=" + studyId + ", healthCode=" + healthCode + ", clientInfo=" + clientInfo
                + ", userDataGroups=" + userDataGroups + ", languages=" + languages + "]";
    }

    public static class Builder {
        private StudyIdentifier studyId;
        private String healthCode;
        private ClientInfo clientInfo;
        private Set<String> userDataGroups;
        private LinkedHashSet<String> languages;

        public Builder withStudyIdentifier(StudyIdentifier studyId) {
            this.studyId = studyId;
            return this;
        }
        public Builder withHealthCode(String healthCode) {
            this.healthCode = healthCode;
            return this;
        }
        public Builder withClientInfo(ClientInfo clientInfo) {
            this.clientInfo = clientInfo;
            return this;
        }
        public Builder withUserDataGroups(Set<String> userDataGroups) {
            this.userDataGroups = userDataGroups;
            return this;
        }
        public Builder withLanguages(LinkedHashSet<String> languages) {
            this.languages = languages;
            return this;
        }
        public Builder withContext(CriteriaContext context) {
            this.studyId = context.studyId;
            this.healthCode = context.healthCode;
            this.clientInfo = context.clientInfo;
            this.userDataGroups = context.userDataGroups;
            this.languages = context.languages;
            return this;
        }

        public CriteriaContext build() {
            checkNotNull(studyId, "studyId cannot be null");
            if (clientInfo == null) {
                clientInfo = ClientInfo.UNKNOWN_CLIENT;
            }
            return new CriteriaContext(studyId, healthCode, clientInfo, userDataGroups, languages);
        }
    }
}
