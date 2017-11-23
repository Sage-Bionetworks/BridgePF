package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Set;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.oauth.OAuthAccessGrant;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoOAuthAccessGrantDaoTest {
    
    private static final StudyIdentifier STUDY_ID = new StudyIdentifierImpl("TestClass");
    private static final String VENDOR_ID = "vendorId";
    private static final String HEALTH_CODE = "healthCode";
    private static final long CREATED_ON = DateTime.now().minusDays(1).getMillis();
    private static final long EXPIRES_ON = DateTime.now().plusDays(1).getMillis();

    @Autowired
    public DynamoOAuthAccessGrantDao dao;
    
    @Test
    public void canCRUD() {
        OAuthAccessGrant grant = OAuthAccessGrant.create();
        grant.setHealthCode(HEALTH_CODE);
        grant.setAccessToken("anAccessToken");
        grant.setRefreshToken("aRefreshToken");
        grant.setCreatedOn(CREATED_ON);
        grant.setExpiresOn(EXPIRES_ON);
        grant.setVendorId(VENDOR_ID);
        
        dao.saveAccessGrant(STUDY_ID, grant);
        
        OAuthAccessGrant persisted = dao.getAccessGrant(STUDY_ID, VENDOR_ID, HEALTH_CODE);
        assertEquals(HEALTH_CODE, persisted.getHealthCode());
        assertEquals("anAccessToken", persisted.getAccessToken());
        assertEquals("aRefreshToken", persisted.getRefreshToken());
        assertEquals(CREATED_ON, persisted.getCreatedOn());
        assertEquals(EXPIRES_ON, persisted.getExpiresOn());
        
        persisted.setAccessToken("anotherAccessToken");
        persisted.setRefreshToken("anotherRefreshToken");
        dao.saveAccessGrant(STUDY_ID, persisted);
        
        dao.deleteAccessGrant(STUDY_ID, VENDOR_ID, HEALTH_CODE);
        
        OAuthAccessGrant deleted = dao.getAccessGrant(STUDY_ID, VENDOR_ID, HEALTH_CODE);
        assertNull(deleted);
    }
    
    @Test
    public void canPageRecords() {
        try {
            for (int i=0; i < 10; i++) {
                OAuthAccessGrant grant = OAuthAccessGrant.create();
                grant.setHealthCode(HEALTH_CODE+i);
                grant.setVendorId(VENDOR_ID);
                dao.saveAccessGrant(STUDY_ID, grant);
            }
            Set<String> uniqueHealthCodes = Sets.newHashSet();
            
            ForwardCursorPagedResourceList<OAuthAccessGrant> grants = dao.getAccessGrants(STUDY_ID, VENDOR_ID, null, 5);
            for (OAuthAccessGrant grant : grants.getItems()) {
                uniqueHealthCodes.add(grant.getHealthCode());
            }
            
            grants = dao.getAccessGrants(STUDY_ID, VENDOR_ID, grants.getNextPageOffsetKey(), 5);
            for (OAuthAccessGrant grant : grants.getItems()) {
                uniqueHealthCodes.add(grant.getHealthCode());
            }
            assertNull(grants.getNextPageOffsetKey());
            assertEquals(10, uniqueHealthCodes.size());
        } finally {
            for (int i=0; i < 10; i++) {
                String healthCode = HEALTH_CODE+i;
                dao.deleteAccessGrant(STUDY_ID, VENDOR_ID, healthCode);
            }
        }
    }
}
