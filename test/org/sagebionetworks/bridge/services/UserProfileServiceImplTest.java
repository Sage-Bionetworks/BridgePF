package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserProfile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class UserProfileServiceImplTest {
    
    @Resource
    private UserProfileServiceImpl service;
    
    @Resource
    private TestUserAdminHelper helper;
    
    @Resource
    private UserProfileService profileService;
    
    private TestUser testUser;
    
    @Before
    public void before() {
        testUser = helper.createUser(UserProfileServiceImplTest.class);
    }
    
    @After
    public void after() {
        helper.deleteUser(testUser);
    }
    
    @Test
    public void canUpdateUserProfile() {
        UserProfile userProfile = profileService.getProfile(testUser.getEmail());
        userProfile.setFirstName("Test");
        userProfile.setLastName("Powers");
        userProfile.setPhone("123-456-7890");

        User updatedUser = service.updateProfile(testUser.getUser(), userProfile);
        
        userProfile = profileService.getProfile(testUser.getEmail());
        
        assertEquals("Test", updatedUser.getFirstName());
        assertEquals("Powers", updatedUser.getLastName());
        
        assertEquals("Test", userProfile.getFirstName());
        assertEquals("Powers", userProfile.getLastName());
        assertEquals(testUser.getEmail(), userProfile.getEmail());
        assertEquals(testUser.getUsername(), userProfile.getUsername());
        assertEquals("123-456-7890", userProfile.getPhone());
    }
}
