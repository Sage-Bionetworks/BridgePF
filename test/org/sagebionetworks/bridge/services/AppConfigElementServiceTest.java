package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.AppConfigElementDao;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.EntityPublishedException;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;

import com.google.common.collect.ImmutableList;

@RunWith(MockitoJUnitRunner.class)
public class AppConfigElementServiceTest {
    
    private static final List<AppConfigElement> ELEMENTS = ImmutableList.of(AppConfigElement.create(), AppConfigElement.create());
    private static final VersionHolder VERSION_HOLDER = new VersionHolder(TestUtils.getAppConfigElement().getVersion());
    
    @Mock
    private AppConfigElementDao dao;
    
    @Captor
    private ArgumentCaptor<AppConfigElement> elementCaptor;
    
    private AppConfigElementService service;
    
    @Before
    public void before() {
        service = new AppConfigElementService();
        service.setAppConfigElementDao(dao);
    }

    @Test
    public void getMostRecentElementsIncludesDeleted() {
        when(dao.getMostRecentElements(TEST_STUDY, true)).thenReturn(ELEMENTS);
        
        List<AppConfigElement> returnedElements = service.getMostRecentElements(TEST_STUDY, true);
        
        assertEquals(2, returnedElements.size());
        verify(dao).getMostRecentElements(TEST_STUDY, true);
    }
    
    @Test
    public void getMostRecentElementsExcludesDeleted() {
        when(dao.getMostRecentElements(TEST_STUDY, false)).thenReturn(ELEMENTS);
        
        List<AppConfigElement> returnedElements = service.getMostRecentElements(TEST_STUDY, false);
        
        assertEquals(2, returnedElements.size());
        verify(dao).getMostRecentElements(TEST_STUDY, false);
    }
    
    @Test
    public void createElement() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        element.setDeleted(true);
        
        service.createElement(TEST_STUDY, element);
        
        verify(dao).saveElementRevision(elementCaptor.capture());
        
        // These have been correctly reset
        assertEquals(new Long(1), elementCaptor.getValue().getRevision());
        AppConfigElement captured = elementCaptor.getValue();
        assertNull(captured.getVersion());
        assertFalse(captured.isDeleted());
        assertEquals("api", captured.getStudyId());
        assertEquals("api:id", captured.getKey());
    }
    
    @Test(expected = EntityAlreadyExistsException.class)
    public void createElementThatExists() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        when(dao.getElementRevision(TEST_STUDY, element.getId(), 1L)).thenReturn(element);
        
        service.createElement(TEST_STUDY, element);
    }

    @Test
    public void getElementRevisionsIncludeDeleted() {
        when(dao.getElementRevisions(TEST_STUDY, "id", true)).thenReturn(ELEMENTS);
        
        List<AppConfigElement> returnedElements = service.getElementRevisions(TEST_STUDY, "id", true);
        assertEquals(2, returnedElements.size());
        
        verify(dao).getElementRevisions(TEST_STUDY, "id", true);
    }
    
    @Test
    public void getElementRevisionsExcludeDeleted() {
        when(dao.getElementRevisions(TEST_STUDY, "id", false)).thenReturn(ELEMENTS);
        
        List<AppConfigElement> returnedElements = service.getElementRevisions(TEST_STUDY, "id", false);
        assertEquals(2, returnedElements.size());
        
        verify(dao).getElementRevisions(TEST_STUDY, "id", false);
    }
    
    @Test
    public void createElementRevision() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        element.setDeleted(true);
        when(dao.saveElementRevision(element)).thenReturn(VERSION_HOLDER);
        
        VersionHolder returnedVersion = service.createElementRevision(TEST_STUDY, element);
        assertEquals(VERSION_HOLDER, returnedVersion);
        
        verify(dao).saveElementRevision(elementCaptor.capture());
        AppConfigElement captured = elementCaptor.getValue();
        assertFalse(captured.isDeleted());
        assertEquals("api", captured.getStudyId());
        assertEquals("api:id", captured.getKey());
    }

    @Test(expected = EntityAlreadyExistsException.class)
    public void createElementRevisionThatAlreadyExists() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        when(dao.getElementRevision(TEST_STUDY, element.getId(), element.getRevision())).thenReturn(element);
        
        service.createElementRevision(TEST_STUDY, element);
    }
    
    @Test
    public void getMostRecentlyPublishedElement() {
        service.getMostRecentlyPublishedElement(TEST_STUDY, "id");
        
        verify(dao).getMostRecentlyPublishedElement(TEST_STUDY, "id");
    }

    @Test
    public void getElementRevision() {
        service.getElementRevision(TEST_STUDY, "id", 3L);
        
        verify(dao).getElementRevision(TEST_STUDY, "id", 3L);
    }

    @Test
    public void updateElementRevision() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        when(dao.getElementRevision(TEST_STUDY, element.getId(), element.getRevision())).thenReturn(element);
        
        service.updateElementRevision(TEST_STUDY, element);
        
        verify(dao).saveElementRevision(elementCaptor.capture());
        assertEquals("api", elementCaptor.getValue().getStudyId());
        assertEquals("api:id", elementCaptor.getValue().getKey());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void updateElementRevisionThatIsLogicallDeleted() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        element.setDeleted(true); // true as persisted and updated, should throw ENFE
        when(dao.getElementRevision(TEST_STUDY, element.getId(), element.getRevision())).thenReturn(element);
        
        service.updateElementRevision(TEST_STUDY, element);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void updateElementRevisionThatDoesNotExist() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        when(dao.getElementRevision(TEST_STUDY, element.getId(), element.getRevision())).thenReturn(null);
        
        service.updateElementRevision(TEST_STUDY, element);
    }
    
    @Test
    public void updateElementRevisionDeletesRevision() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        element.setDeleted(true);
        
        AppConfigElement persisted = TestUtils.getAppConfigElement();
        persisted.setDeleted(false);
        when(dao.getElementRevision(TEST_STUDY, element.getId(), element.getRevision())).thenReturn(persisted);
        
        service.updateElementRevision(TEST_STUDY, element);
        
        verify(dao).saveElementRevision(elementCaptor.capture());
        assertTrue(elementCaptor.getValue().isDeleted());
    }
    
    @Test
    public void updateElementRevisionUndeletesRevision() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        element.setDeleted(false);
        
        AppConfigElement persisted = TestUtils.getAppConfigElement();
        persisted.setDeleted(true);
        when(dao.getElementRevision(TEST_STUDY, element.getId(), element.getRevision())).thenReturn(persisted);
        
        service.updateElementRevision(TEST_STUDY, element);
        
        verify(dao).saveElementRevision(elementCaptor.capture());
        assertFalse(elementCaptor.getValue().isDeleted());
    }
    
    @Test
    public void publishElementRevision() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        element.setPublished(false);
        when(dao.getElementRevision(TEST_STUDY, element.getId(), element.getRevision())).thenReturn(element);
        
        service.publishElementRevision(TEST_STUDY, "id", 3L);
        
        verify(dao).saveElementRevision(elementCaptor.capture());
        assertTrue(elementCaptor.getValue().isPublished());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void publisheElementThatDoesNotExist() {
        service.publishElementRevision(TEST_STUDY, "id", 3L);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void publishElementThatIsLogicallDeleted() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        element.setDeleted(true);
        when(dao.getElementRevision(TEST_STUDY, element.getId(), element.getRevision())).thenReturn(element);
        
        service.publishElementRevision(TEST_STUDY, "id", 3L);
    }
    
    @Test(expected = EntityPublishedException.class)
    public void publishElementThatIsPublished() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        element.setPublished(true);
        when(dao.getElementRevision(TEST_STUDY, element.getId(), element.getRevision())).thenReturn(element);
        
        service.publishElementRevision(TEST_STUDY, "id", 3L);
    }
    
    @Test
    public void deleteElementAllRevisions() {
        when(dao.getElementRevisions(TEST_STUDY, "id", false)).thenReturn(ELEMENTS);        
        
        service.deleteElementAllRevisions(TEST_STUDY, "id");
        
        verify(dao, times(2)).saveElementRevision(elementCaptor.capture());
        assertTrue(elementCaptor.getAllValues().get(0).isDeleted());
        assertTrue(elementCaptor.getAllValues().get(1).isDeleted());
    }
    
    @Test
    public void deleteElementAllRevisionsPermanently() {
        ELEMENTS.get(0).setId("id");
        ELEMENTS.get(0).setRevision(1L);
        ELEMENTS.get(1).setId("id");
        ELEMENTS.get(1).setRevision(2L);
        when(dao.getElementRevisions(TEST_STUDY, "id", true)).thenReturn(ELEMENTS);        
        
        service.deleteElementAllRevisionsPermanently(TEST_STUDY, "id");
        
        verify(dao).getElementRevisions(TEST_STUDY, "id", true);
        verify(dao).deleteElementRevisionPermanently(TEST_STUDY, "id", 1);
        verify(dao).deleteElementRevisionPermanently(TEST_STUDY, "id", 2);
    }
    
    @Test
    public void deleteElementRevision() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        element.setDeleted(false);
        when(dao.getElementRevision(TEST_STUDY, element.getId(), element.getRevision())).thenReturn(element);
        
        service.deleteElementRevision(TEST_STUDY, "id", 3L);
        
        verify(dao).saveElementRevision(elementCaptor.capture());
        assertTrue(elementCaptor.getValue().isDeleted());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void deleteElementRevisionThatDoesNotExist() {
        service.deleteElementRevision(TEST_STUDY, "id", 3L);
    }
    
    @Test
    public void deleteElementRevisionPermanently() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        element.setDeleted(true); // this does not matter. You can permanently delete logically deleted entities.
        when(dao.getElementRevision(TEST_STUDY, element.getId(), element.getRevision())).thenReturn(element);
        
        service.deleteElementRevisionPermanently(TEST_STUDY, "id", 3L);
        
        verify(dao).deleteElementRevisionPermanently(TEST_STUDY, "id", 3L);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void deleteElementRevisionPermanentlyThatDoesNotExist() {
        service.deleteElementRevisionPermanently(TEST_STUDY, "id", 3L);
    }    
}
