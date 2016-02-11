package org.sagebionetworks.bridge.models;

import java.util.Objects;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

public final class CriteriaContext {
    
    private final ClientInfo clientInfo;
    private final Set<String> userDataGroups;
    
    private CriteriaContext(ClientInfo clientInfo, Set<String> userDataGroups) {
        this.clientInfo = clientInfo;
        this.userDataGroups = (userDataGroups == null) ? ImmutableSet.of() : ImmutableSet.copyOf(userDataGroups);
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

    @Override
    public int hashCode() {
        return Objects.hash(clientInfo, userDataGroups);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        CriteriaContext other = (CriteriaContext)obj;
        return (Objects.equals(clientInfo, other.clientInfo) && Objects.equals(userDataGroups, other.userDataGroups));
    }

    @Override
    public String toString() {
        return "CriteriaContext [clientInfo=" + clientInfo + ", userDataGroups=" + userDataGroups + "]";
    }

    public static class Builder {
        private ClientInfo clientInfo;
        private Set<String> userDataGroups;

        public Builder withClientInfo(ClientInfo clientInfo) {
            this.clientInfo = clientInfo;
            return this;
        }
        public Builder withUserDataGroups(Set<String> userDataGroups) {
            this.userDataGroups = userDataGroups;
            return this;
        }
        public Builder withContext(CriteriaContext context) {
            this.clientInfo = context.clientInfo;
            this.userDataGroups = context.userDataGroups;
            return this;
        }

        public CriteriaContext build() {
            if (clientInfo == null) {
                clientInfo = ClientInfo.UNKNOWN_CLIENT;
            }
            return new CriteriaContext(clientInfo, userDataGroups);
        }
    }
}
