package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.oauth.OAuthAccessGrant;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface OAuthAccessGrantDao {

    ForwardCursorPagedResourceList<OAuthAccessGrant> getAccessGrants(StudyIdentifier studyId, String vendorId, String offsetKey,
            int pageSize);
    
    public OAuthAccessGrant getAccessGrant(StudyIdentifier studyId, String vendorId, String healthCode);
    
    public OAuthAccessGrant saveAccessGrant(StudyIdentifier studyId, OAuthAccessGrant grant);
    
    public void deleteAccessGrant(StudyIdentifier studyId, String vendorId, String healthCode);
    
}
