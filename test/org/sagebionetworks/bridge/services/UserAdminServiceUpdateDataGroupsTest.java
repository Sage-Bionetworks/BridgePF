package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.HealthIdDao;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.NotFoundException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.DataGroups;

public class UserAdminServiceUpdateDataGroupsTest {
    private static final Set<String> TEST_DATA_GROUP_SET = ImmutableSet.of("foo", "bar");
    private static final DataGroups TEST_DATA_GROUPS = new DataGroups(TEST_DATA_GROUP_SET);
    private static final String TEST_EMAIL = "email@example.com";
    private static final String TEST_HEALTH_ID = "test-health-id";
    private static final String TEST_HEALTH_CODE = "test-health-code";
    private static final DynamoStudy TEST_STUDY;
    static {
        // Only thing we care about is data groups
        TEST_STUDY = new DynamoStudy();
        TEST_STUDY.setDataGroups(ImmutableSet.of("foo", "bar", "extraneous"));
    }

    @Test(expected = BadRequestException.class)
    public void nullEmail() {
        new UserAdminService().updateDataGroupForUser(TEST_STUDY, null, TEST_DATA_GROUPS);
    }

    @Test(expected = BadRequestException.class)
    public void emptyEmail() {
        new UserAdminService().updateDataGroupForUser(TEST_STUDY, "", TEST_DATA_GROUPS);
    }

    @Test(expected = BadRequestException.class)
    public void blankEmail() {
        new UserAdminService().updateDataGroupForUser(TEST_STUDY, "   ", TEST_DATA_GROUPS);
    }

    @Test(expected = InvalidEntityException.class)
    public void invalidDataGroup() {
        new UserAdminService().updateDataGroupForUser(TEST_STUDY, TEST_EMAIL, new DataGroups(ImmutableSet.of("foo",
                "bar", "invalid")));
    }

    @Test(expected = NotFoundException.class)
    public void noAccount() {
        // mock account DAO
        AccountDao mockAccountDao = mock(AccountDao.class);
        when(mockAccountDao.getAccount(TEST_STUDY, TEST_EMAIL)).thenReturn(null);

        // set up service
        UserAdminService svc = new UserAdminService();
        svc.setAccountDao(mockAccountDao);

        // execute
        svc.updateDataGroupForUser(TEST_STUDY, TEST_EMAIL, TEST_DATA_GROUPS);
    }

    @Test(expected = NotFoundException.class)
    public void noHealthCode() {
        // mock account DAO
        Account mockAccount = mock(Account.class);
        when(mockAccount.getHealthId()).thenReturn(TEST_HEALTH_ID);

        AccountDao mockAccountDao = mock(AccountDao.class);
        when(mockAccountDao.getAccount(TEST_STUDY, TEST_EMAIL)).thenReturn(mockAccount);

        // mock health ID DAO
        HealthIdDao mockHealthIdDao = mock(HealthIdDao.class);
        when(mockHealthIdDao.getCode(TEST_HEALTH_ID)).thenReturn(null);

        // set up service
        UserAdminService svc = new UserAdminService();
        svc.setAccountDao(mockAccountDao);
        svc.setHealthIdDao(mockHealthIdDao);

        // execute
        svc.updateDataGroupForUser(TEST_STUDY, TEST_EMAIL, TEST_DATA_GROUPS);
    }

    @Test
    public void normalCase() {
        // mock account DAO
        Account mockAccount = mock(Account.class);
        when(mockAccount.getHealthId()).thenReturn(TEST_HEALTH_ID);

        AccountDao mockAccountDao = mock(AccountDao.class);
        when(mockAccountDao.getAccount(TEST_STUDY, TEST_EMAIL)).thenReturn(mockAccount);

        // mock health ID DAO
        HealthIdDao mockHealthIdDao = mock(HealthIdDao.class);
        when(mockHealthIdDao.getCode(TEST_HEALTH_ID)).thenReturn(TEST_HEALTH_CODE);

        // mock options service
        ParticipantOptionsService mockOptionsSvc = mock(ParticipantOptionsService.class);

        // set up service
        UserAdminService svc = new UserAdminService();
        svc.setAccountDao(mockAccountDao);
        svc.setHealthIdDao(mockHealthIdDao);
        svc.setParticipantOptionsService(mockOptionsSvc);

        // execute and validate
        svc.updateDataGroupForUser(TEST_STUDY, TEST_EMAIL, TEST_DATA_GROUPS);
        verify(mockOptionsSvc).setStringSet(TEST_STUDY, TEST_HEALTH_CODE, ParticipantOption.DATA_GROUPS,
                TEST_DATA_GROUP_SET);
    }
}
