package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.FPHSExternalIdentifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Lists;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoFPHSExternalIdentifierDaoTest {

    @Resource
    private DynamoFPHSExternalIdentifierDao dao;
    
    private List<String> idsToDelete;
    
    @Before
    public void before() {
        idsToDelete = Lists.newArrayList();
    }
    
    @After
    public void after() {
        idsToDelete.forEach(dao::deleteExternalId);
    }
    
    private String getId() {
        return TestUtils.randomName(DynamoFPHSExternalIdentifierDaoTest.class);
    }
    
    @Test(expected=EntityNotFoundException.class)
    public void verifyExternalIdDoesNotExist() throws Exception {
        ExternalIdentifier externalId = new ExternalIdentifier(getId());
        
        dao.verifyExternalId(externalId);
    }
    
    @Test
    public void verifyExternalIdAlreadyRegistered() throws Exception {
        DynamoFPHSExternalIdentifier id1 = new DynamoFPHSExternalIdentifier(getId());
        DynamoFPHSExternalIdentifier id2 = new DynamoFPHSExternalIdentifier(getId());
        dao.addExternalIds(Lists.newArrayList(id1, id2));
        idsToDelete.add(id1.getExternalId());
        idsToDelete.add(id2.getExternalId());
        
        dao.registerExternalId(new ExternalIdentifier(id2.getExternalId()));
        
        dao.verifyExternalId(new ExternalIdentifier(id1.getExternalId()));
        try {
            dao.verifyExternalId(new ExternalIdentifier(id2.getExternalId()));
            fail("Exception should have been throw");
        } catch(EntityAlreadyExistsException e) {
        }
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void registerUnknownCodeThrowsException() throws Exception {
        dao.registerExternalId(new ExternalIdentifier(getId()));
    }
    
    @Test(expected = EntityAlreadyExistsException.class)
    public void registerCodeAgainThrowsException() throws Exception {
        DynamoFPHSExternalIdentifier id1 = new DynamoFPHSExternalIdentifier(getId());
        dao.addExternalIds(Lists.newArrayList(id1));
        idsToDelete.add(id1.getExternalId());
        
        dao.registerExternalId(new ExternalIdentifier(id1.getExternalId()));
        dao.registerExternalId(new ExternalIdentifier(id1.getExternalId()));
    }
    
    @Test
    public void registerValidCodeOK() throws Exception {
        DynamoFPHSExternalIdentifier id1 = new DynamoFPHSExternalIdentifier(getId());
        dao.addExternalIds(Lists.newArrayList(id1));
        idsToDelete.add(id1.getExternalId());
        
        dao.registerExternalId(new ExternalIdentifier(id1.getExternalId()));
        FPHSExternalIdentifier found = getById(dao.getExternalIds(), id1);
        assertTrue(found.isRegistered());
    }
    
    @Test
    public void getAndAddExternalIds() throws Exception {
        DynamoFPHSExternalIdentifier id1 = new DynamoFPHSExternalIdentifier(getId());
        DynamoFPHSExternalIdentifier id2 = new DynamoFPHSExternalIdentifier(getId());
        dao.addExternalIds(Lists.newArrayList(id1, id2));
        idsToDelete.add(id1.getExternalId());
        idsToDelete.add(id2.getExternalId());
        
        dao.registerExternalId(new ExternalIdentifier(id1.getExternalId()));
        
        List<FPHSExternalIdentifier> results = dao.getExternalIds();
        assertTrue(results.size() >= 2);

        FPHSExternalIdentifier found = getById(results, id1);
        assertEquals(found.getExternalId(), id1.getExternalId());
        assertTrue(found.isRegistered());

        found = getById(results, id2);
        assertEquals(found.getExternalId(), id2.getExternalId());
        assertFalse(found.isRegistered());
        
        // Now add 1 through 3 again, only 3 should be added.
        DynamoFPHSExternalIdentifier id3 = new DynamoFPHSExternalIdentifier(getId());
        dao.addExternalIds(Lists.newArrayList(id1, id2, id3));
        idsToDelete.add(id3.getExternalId());
        
        results = dao.getExternalIds();
        assertTrue(results.size() >= 3);
        assertTrue(getById(results, id1).isRegistered());
        assertFalse(getById(results, id2).isRegistered());
        assertFalse(getById(results, id3).isRegistered());
    }

    @Test
    public void registerAnUnregisterAnIdentifier() {
        String identifier = getId();
        DynamoFPHSExternalIdentifier id1 = new DynamoFPHSExternalIdentifier(identifier);
        dao.addExternalIds(Lists.newArrayList(id1));
        idsToDelete.add(id1.getExternalId());
        
        dao.registerExternalId(new ExternalIdentifier(identifier));
        dao.unregisterExternalId(new ExternalIdentifier(identifier));
        
        List<FPHSExternalIdentifier> results = dao.getExternalIds();
        FPHSExternalIdentifier found = getById(results, FPHSExternalIdentifier.create(identifier));
        assertFalse(found.isRegistered());
    }
    
    @Test
    public void unregistrationFailsSilentlyIfCannotRollback() {
        // If you unregister, and the identifier either doesn't exist or isn't registered, then 
        // this should just silently pass... we don't need an exception here as the state is 
        // where we want it and we're not changing anything.
        
        // Not found, not a problem (no exception)
        dao.unregisterExternalId(new ExternalIdentifier("foo"));
        
        // Found but not registered is not a problem (no exception & comes back unregistered)
        DynamoFPHSExternalIdentifier id1 = new DynamoFPHSExternalIdentifier(getId());
        dao.addExternalIds(Lists.newArrayList(id1));
        idsToDelete.add(id1.getExternalId());
        
        dao.unregisterExternalId(new ExternalIdentifier(id1.getExternalId()));
        
        List<FPHSExternalIdentifier> results = dao.getExternalIds();
        FPHSExternalIdentifier found = getById(results, id1);
        assertFalse(found.isRegistered());
    }

    @Test(expected = BadRequestException.class)
    public void addExternalIdsExceedsLimit() {
        List<FPHSExternalIdentifier> idList = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            String id = getId();
            idList.add(new DynamoFPHSExternalIdentifier(id));
            idsToDelete.add(id);
        }
        dao.addExternalIds(idList);
    }

    private FPHSExternalIdentifier getById(List<FPHSExternalIdentifier> identifiers, FPHSExternalIdentifier externalId) {
        for (FPHSExternalIdentifier identifier : identifiers) {
            if (identifier.getExternalId().equals(externalId.getExternalId())) {
                return identifier;
            }
        }
        return null;
    }
}
