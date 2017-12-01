package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_KEY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;

import java.util.List;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.dao.OAuthAccessGrantDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.oauth.OAuthAccessGrant;
import org.sagebionetworks.bridge.models.oauth.OAuthAccessToken;
import org.sagebionetworks.bridge.models.oauth.OAuthAuthorizationToken;
import org.sagebionetworks.bridge.models.studies.OAuthProvider;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OAuthService {

    private StudyService studyService;
    
    private OAuthAccessGrantDao grantDao;
    
    private OAuthProviderService providerService;
    
    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    @Autowired
    final void setOAuthAccessGrantDao(OAuthAccessGrantDao grantDao) {
        this.grantDao = grantDao;
    }
    
    @Autowired
    final void setOAuthProviderService(OAuthProviderService providerService) {
        this.providerService = providerService;
    }
    
    protected DateTime getDateTime() {
        return DateTime.now(DateTimeZone.UTC);
    }
    
    public ForwardCursorPagedResourceList<String> getHealthCodesGrantingAccess(StudyIdentifier studyId,
            String vendorId, int pageSize, String offsetKey) {
        checkNotNull(studyId);
        checkNotNull(vendorId);
        
        ForwardCursorPagedResourceList<OAuthAccessGrant> list = grantDao.getAccessGrants(studyId, vendorId,
                offsetKey, pageSize);

        List<String> healthCodes = list.getItems().stream().map(OAuthAccessGrant::getHealthCode)
                .collect(Collectors.toList());
        
        return new ForwardCursorPagedResourceList<String>(healthCodes, list.getNextPageOffsetKey())
                .withRequestParam(PAGE_SIZE, pageSize)
                .withRequestParam(OFFSET_KEY, offsetKey);
    }
    
    public OAuthAccessToken requestAccessToken(StudyIdentifier studyId, String healthCode, OAuthAuthorizationToken authToken) {
        checkNotNull(studyId);
        checkNotNull(healthCode);
        checkNotNull(authToken);
        
        return retrieveAccessToken(studyId, authToken.getVendorId(), healthCode, authToken);
    }
    
    public OAuthAccessToken getAccessToken(StudyIdentifier studyId, String vendorId, String healthCode) {
        checkNotNull(studyId);
        checkNotNull(vendorId);
        checkNotNull(healthCode);
        
        return retrieveAccessToken(studyId, vendorId, healthCode, null);
    }
    
    private OAuthAccessToken retrieveAccessToken(StudyIdentifier studyId, String vendorId, String healthCode,
            OAuthAuthorizationToken authToken) {
        checkNotNull(studyId);
        checkNotNull(vendorId);
        checkNotNull(healthCode);
        
        Study study = studyService.getStudy(studyId);
        OAuthProvider provider = study.getOAuthProviders().get(vendorId);
        if (provider == null) {
            throw new EntityNotFoundException(OAuthProvider.class);
        }
        OAuthAccessGrant grant = null;
        
        if (authToken != null && authToken.getAuthToken() != null) {
            grant = providerService.requestAccessGrant(provider, authToken);
        } else {
            grant = grantDao.getAccessGrant(studyId, vendorId, healthCode);
        }
        try {
            if (grant == null) {
                throw new EntityNotFoundException(OAuthAccessGrant.class);
            } else if (getDateTime().isAfter(grant.getExpiresOn())) {
                grant = providerService.refreshAccessGrant(provider, vendorId, grant.getRefreshToken());
            }
        } catch(Exception e) {
            if (grant != null) {
                grantDao.deleteAccessGrant(studyId, vendorId, healthCode);
            }
            throw e;
        }
        grant.setVendorId(vendorId);
        grant.setHealthCode(healthCode);
        grantDao.saveAccessGrant(studyId, grant);
        return getTokenForGrant(grant);
    }
    
    private OAuthAccessToken getTokenForGrant(OAuthAccessGrant grant) {
        DateTime expiresOn = new DateTime(grant.getExpiresOn(), DateTimeZone.UTC);
        return new OAuthAccessToken(grant.getVendorId(), grant.getAccessToken(), expiresOn, grant.getProviderUserId());
    }
}
