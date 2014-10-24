package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserProfile;
import org.sagebionetworks.bridge.models.UserSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class UserProfileServiceImplTest {
    
    @Resource
    private UserProfileServiceImpl service;
    
    @Resource
    private TestUserAdminHelper helper;
    
    private UserSession session;
    
    @Before
    public void before() {
        session = helper.createUser(getClass().getSimpleName());
    }
    
    @After
    public void after() {
        helper.deleteUser(session, getClass().getSimpleName());
    }
    
    @Test
    public void canUpdateUserProfile() {
        UserProfile userProfile = helper.getUserProfile(session);
        userProfile.setFirstName("Test");
        userProfile.setLastName("Powers");
        
        User updatedUser = service.updateProfile(session.getUser(), userProfile);
        
        assertEquals("Test", updatedUser.getFirstName());
        assertEquals("Powers", updatedUser.getLastName());
    }
}
