package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.ResearchConsent;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.UserSession;

public interface ConsentService {

    public UserSession give(String sessionToken, ResearchConsent consent, Study study, boolean sendEmail);
}
