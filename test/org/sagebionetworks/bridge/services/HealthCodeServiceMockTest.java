package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.junit.Test;

import org.sagebionetworks.bridge.dao.HealthIdDao;
import org.sagebionetworks.bridge.models.accounts.HealthId;

public class HealthCodeServiceMockTest {
    @Test
    public void invalidHealthIdReturnsNull() {
        HealthIdDao dao = mock(HealthIdDao.class);
        HealthCodeService healthCodeService = new HealthCodeService();
        healthCodeService.setHealthIdDao(dao);

        HealthId healthId = healthCodeService.getMapping(null);
        assertNull(healthId);

        verifyZeroInteractions(dao);
    }

    @Test
    public void invalidHealthIdReturnsNullNotInvalidHealthIdObject() {
        HealthIdDao dao = mock(HealthIdDao.class);
        when(dao.getCode("123")).thenReturn("abc");
        when(dao.getCode("456")).thenReturn(null);

        HealthCodeService healthCodeService = new HealthCodeService();
        healthCodeService.setHealthIdDao(dao);

        // valid
        HealthId id1 = healthCodeService.getMapping("123");
        assertEquals("123", id1.getId());
        assertEquals("abc", id1.getCode());

        // invalid... should return no object at all.
        HealthId id2 = healthCodeService.getMapping("456");
        assertNull(id2);
    }
}