package org.sagebionetworks.bridge.models.studies;

/**
 * simple POJO wrapping project and team id to send back to client
 */
public final class SynapseProjectIdTeamIdHolder {
    private final String projectId;
    private final Long teamId;

    public SynapseProjectIdTeamIdHolder(String projectId, Long teamId) {
        this.projectId = projectId;
        this.teamId = teamId;
    }

    public String getProjectId() {
        return projectId;
    }

    public Long getTeamId() {
        return teamId;
    }

}
