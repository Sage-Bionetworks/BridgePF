package org.sagebionetworks.bridge.models.studies;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;

public final class StudyAndUserHolder {
    private final Study study;
    private final List<StudyParticipant> users;

    public StudyAndUserHolder(@JsonProperty("study")Study study, @JsonProperty("users")List<StudyParticipant> users) {
        this.study = study;
        this.users = users;
    }

    public List<StudyParticipant> getUsers() {
        return users;
    }

    public Study getStudy() {
        return study;
    }
}
