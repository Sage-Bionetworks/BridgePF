package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.Roles.TEST_USERS;

import javax.annotation.Resource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.services.UserAdminService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class ManualCleanupTest {

    @Resource
    UserAdminService userAdminService;

    @Test
    public void deleteAllRemainingTestUsers() {
        userAdminService.deleteAllUsers(TEST_USERS);
    }
}
