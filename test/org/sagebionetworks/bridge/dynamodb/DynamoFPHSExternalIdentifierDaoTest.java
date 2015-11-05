package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
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
    
    @BeforeClass
    public static void beforeClass() {
        DynamoInitializer.init(DynamoFPHSExternalIdentifier.class);
    }

    @After
    public void after() {
        dao.deleteAll();
    }
    
    @Test
    public void verifyExternalIdDoesNotExist() throws Exception {
        ExternalIdentifier externalId = new ExternalIdentifier("abc");
        
        assertFalse(dao.verifyExternalId(externalId));
    }
    
    @Test
    public void verifyExternalIdAlreadyRegistered() throws Exception {
        DynamoFPHSExternalIdentifier id1 = new DynamoFPHSExternalIdentifier("bar");
        DynamoFPHSExternalIdentifier id2 = new DynamoFPHSExternalIdentifier("baz");
        dao.addExternalIds(Lists.newArrayList(id1, id2));
        dao.registerExternalId(new ExternalIdentifier(id2.getExternalId()));
        
        assertTrue(dao.verifyExternalId(new ExternalIdentifier("bar")));
        assertFalse(dao.verifyExternalId(new ExternalIdentifier("baz")));
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void registerUnknownCodeThrowsException() throws Exception {
        dao.registerExternalId(new ExternalIdentifier("bar"));
    }
    
    @Test(expected = EntityAlreadyExistsException.class)
    public void registerCodeAgainThrowsException() throws Exception {
        DynamoFPHSExternalIdentifier id1 = new DynamoFPHSExternalIdentifier("baz");
        dao.addExternalIds(Lists.newArrayList(id1));
        dao.registerExternalId(new ExternalIdentifier(id1.getExternalId()));
        
        dao.registerExternalId(new ExternalIdentifier("baz"));
    }
    
    @Test
    public void registerValidCodeOK() throws Exception {
        DynamoFPHSExternalIdentifier id1 = new DynamoFPHSExternalIdentifier("baz");
        dao.addExternalIds(Lists.newArrayList(id1));
        
        dao.registerExternalId(new ExternalIdentifier("baz"));
        
        assertTrue(dao.getExternalIds().get(0).getRegistered());
    }
    
    @Test
    public void getExternalIds() throws Exception {
        DynamoFPHSExternalIdentifier id1 = new DynamoFPHSExternalIdentifier("bar");
        DynamoFPHSExternalIdentifier id2 = new DynamoFPHSExternalIdentifier("baz");
        dao.addExternalIds(Lists.newArrayList(id1, id2));
        dao.registerExternalId(new ExternalIdentifier(id1.getExternalId()));
        
        List<FPHSExternalIdentifier> identifiers = dao.getExternalIds();
        assertEquals(2, identifiers.size());

        FPHSExternalIdentifier identifier = getById(identifiers, "bar");
        assertEquals("bar", identifier.getExternalId());
        assertTrue(identifier.getRegistered());

        identifier = getById(identifiers, "baz");
        assertEquals("baz", identifier.getExternalId());
        assertFalse(identifier.getRegistered());
    }

    @Test
    public void addExternalIds() throws Exception {
        DynamoFPHSExternalIdentifier id1 = new DynamoFPHSExternalIdentifier("bar");
        DynamoFPHSExternalIdentifier id2 = new DynamoFPHSExternalIdentifier("baz");
        dao.addExternalIds(Lists.newArrayList(id1, id2));

        // Register id1. It should stay registered.
        dao.registerExternalId(new ExternalIdentifier(id1.getExternalId()));
        
        // Add a third ID, you will have three IDs, none are registered
        id1 = new DynamoFPHSExternalIdentifier("bar");
        id2 = new DynamoFPHSExternalIdentifier("baz");
        DynamoFPHSExternalIdentifier id3 = new DynamoFPHSExternalIdentifier("boo");
        dao.addExternalIds(Lists.newArrayList(id1, id2, id3));
        
        // Two updates by primary key, and an add for 3 records
        List<FPHSExternalIdentifier> identifiers = dao.getExternalIds();
        assertEquals(3, identifiers.size());
        assertTrue(getById(identifiers, "bar").getRegistered());
        assertFalse(getById(identifiers, "baz").getRegistered());
        assertFalse(getById(identifiers, "boo").getRegistered());
    }
    
    private FPHSExternalIdentifier getById(List<FPHSExternalIdentifier> identifiers, String externalId) {
        for (FPHSExternalIdentifier identifier : identifiers) {
            if (identifier.getExternalId().equals(externalId)) {
                return identifier;
            }
        }
        return null;
    }
}
