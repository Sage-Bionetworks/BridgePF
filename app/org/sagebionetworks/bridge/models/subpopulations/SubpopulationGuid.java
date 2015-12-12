package org.sagebionetworks.bridge.models.subpopulations;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as=SubpopulationGuidImpl.class)
public interface SubpopulationGuid {
    public String getGuid();
}
