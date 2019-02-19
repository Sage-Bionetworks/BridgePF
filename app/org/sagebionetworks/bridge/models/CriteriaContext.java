package org.sagebionetworks.bridge.models;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public final class CriteriaContext {
    
    private final StudyIdentifier studyId;
    private final String healthCode;
    private final String userId;
    private final ClientInfo clientInfo;
    private final String ipAddress;
    private final Set<String> userDataGroups;
    private final Set<String> userSubstudyIds;
    // This set has ordered keys (most to least preferential)
    private final List<String> languages;
    
    private CriteriaContext(StudyIdentifier studyId, String healthCode, String userId, ClientInfo clientInfo,
            String ipAddress, Set<String> userDataGroups, Set<String> userSubstudyIds, List<String> languages) {
        this.studyId = studyId;
        this.healthCode = healthCode;
        this.userId = userId;
        this.clientInfo = clientInfo;
        this.ipAddress = ipAddress;
        this.userDataGroups = (userDataGroups == null) ? ImmutableSet.of() : ImmutableSet.copyOf(userDataGroups);
        this.userSubstudyIds = (userSubstudyIds == null) ? ImmutableSet.of() : ImmutableSet.copyOf(userSubstudyIds);
        this.languages = (languages == null) ? ImmutableList.of() : languages;
    }
    
    public AccountId getAccountId() {
        return AccountId.forId(studyId.getIdentifier(), userId);
    }

    /**
    * Client information based on the supplied User-Agent header.
    */
    public ClientInfo getClientInfo() {
        return clientInfo;
    }

    /** The user's IP Address, as reported by Amazon. */
    public String getIpAddress() {
        return ipAddress;
    }

    public Set<String> getUserDataGroups() {
        return userDataGroups;
    }
    
    public Set<String> getUserSubstudyIds() {
        return userSubstudyIds;
    }
    
    public StudyIdentifier getStudyIdentifier() {
        return studyId;
    }
    
    public String getHealthCode() {
        return healthCode;
    }
    
    public String getUserId() {
        return userId;
    }
    
    /**
     * Languages are sorted in order of descending preference of the LanguageRange objects 
     * submitted by the request via the Accept-Language HTTP header. That is to say, the 
     * client prefers the first language more than the second language, the second more than 
     * the third, etc. However, this isn't a SortedSet, because it is sorted by something 
     * (LanguageRange quality) that is external to the contents of the Set.
     */
    public List<String> getLanguages() {
        return languages;
    }

    @Override
    public int hashCode() {
        return Objects.hash(studyId, healthCode, userId, clientInfo, ipAddress, userDataGroups, userSubstudyIds,
                languages);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        CriteriaContext other = (CriteriaContext)obj;
        return (Objects.equals(clientInfo, other.clientInfo) &&
                Objects.equals(ipAddress, other.ipAddress) &&
                Objects.equals(userDataGroups, other.userDataGroups) &&
                Objects.equals(userSubstudyIds, other.userSubstudyIds) &&
                Objects.equals(studyId, other.studyId) && 
                Objects.equals(healthCode, other.healthCode) && 
                Objects.equals(userId, other.userId) &&
                Objects.equals(languages, other.languages));
    }

    @Override
    public String toString() {
        return "CriteriaContext [studyId=" + studyId + ", userId=" + userId + ", clientInfo=" + clientInfo
                + ", ipAddress=" + ipAddress + ", userDataGroups=" + userDataGroups + ", userSubstudies=" 
                + userSubstudyIds + ", languages=" + languages + "]";
    }

    public static class Builder {
        private StudyIdentifier studyId;
        private String healthCode;
        private String userId;
        private ClientInfo clientInfo;
        private String ipAddress;
        private Set<String> userDataGroups;
        private Set<String> userSubstudyIds;
        private List<String> languages;

        public Builder withStudyIdentifier(StudyIdentifier studyId) {
            this.studyId = studyId;
            return this;
        }
        public Builder withHealthCode(String healthCode) {
            this.healthCode = healthCode;
            return this;
        }
        public Builder withUserId(String userId) {
            this.userId = userId;
            return this;
        }
        public Builder withClientInfo(ClientInfo clientInfo) {
            this.clientInfo = clientInfo;
            return this;
        }

        /** @see #getIpAddress */
        public Builder withIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public Builder withUserDataGroups(Set<String> userDataGroups) {
            this.userDataGroups = userDataGroups;
            return this;
        }
        public Builder withUserSubstudyIds(Set<String> userSubstudyIds) {
            this.userSubstudyIds = userSubstudyIds;
            return this;
        }
        public Builder withLanguages(List<String> languages) {
            this.languages = languages;
            return this;
        }

        public Builder withContext(CriteriaContext context) {
            this.studyId = context.studyId;
            this.healthCode = context.healthCode;
            this.userId = context.userId;
            this.clientInfo = context.clientInfo;
            this.ipAddress = context.ipAddress;
            this.userDataGroups = context.userDataGroups;
            this.userSubstudyIds = context.userSubstudyIds;
            this.languages = context.languages;
            return this;
        }

        public CriteriaContext build() {
            checkNotNull(studyId, "studyId cannot be null");
            if (clientInfo == null) {
                clientInfo = ClientInfo.UNKNOWN_CLIENT;
            }
            return new CriteriaContext(studyId, healthCode, userId, clientInfo, ipAddress, userDataGroups,
                    userSubstudyIds, languages);
        }
    }
}
