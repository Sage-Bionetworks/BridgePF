package org.sagebionetworks.bridge.models.studies;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;

public final class StudyAndUsers implements BridgeEntity {
    private final List<String> adminIds;
    private final Study study;
    private final List<StudyParticipant> users;

    public StudyAndUsers(@JsonProperty("adminIds") List<String> adminIds, @JsonProperty("study") Study study,
            @JsonProperty("users") List<StudyParticipant> users) {
        this.adminIds = adminIds;
        this.study = study;
        this.users = users;
    }

    public List<String> getAdminIds() {
        return adminIds;
    }

    public List<StudyParticipant> getUsers() {
        return users;
    }

    public Study getStudy() {
        return study;
    }
}
