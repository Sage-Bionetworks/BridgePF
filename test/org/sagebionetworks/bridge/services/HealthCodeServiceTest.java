package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.dao.HealthIdDao;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthCode;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthId;
import org.sagebionetworks.bridge.dynamodb.DynamoTestUtil;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.studies.Study;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class HealthCodeServiceTest {

    @Resource
    private StudyService studyService;

    @Resource
    private HealthCodeService healthCodeService;

    @Test
    public void createAndRetrieveHealthId() {
        Study study = studyService.getStudy(TEST_STUDY_IDENTIFIER);
        HealthId mapping = healthCodeService.createMapping(study);
        assertNotNull(mapping);
        assertEquals(mapping.getCode(), healthCodeService.getMapping(mapping.getId()).getCode());
        HealthId healthId2 = healthCodeService.createMapping(study);
        assertFalse(mapping.getId().equals(healthId2.getId()));
        assertFalse(mapping.getCode().equals(healthId2.getCode()));
    }
}
