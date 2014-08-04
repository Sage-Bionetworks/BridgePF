package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.ResearchConsent;
import org.sagebionetworks.bridge.models.Study;

public interface ConsentService {

    void give(String sessionToken, ResearchConsent consent, Study study);
}
