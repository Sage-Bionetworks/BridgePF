package org.sagebionetworks.bridge.services.backfill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.junit.Test;

public class PageIteratorTest {

    private static class WordIterator extends PageIterator<String> {

        private final List<String> words;
        private final int pageSize;
        private int start;

        private WordIterator(String[] words, int pageSize) {
            this.words = Arrays.asList(words);
            this.pageSize = pageSize;
            start = 0;
        }

        @Override
        int pageSize() {
            return pageSize;
        }

        @Override
        Iterator<String> nextPage() {
            int end = start + pageSize;
            if (end > words.size()) {
                end = words.size();
            }
            List<String> page = words.subList(start, end);
            start += pageSize;
            if (start > words.size()) {
                start = words.size();
            }
            return page.iterator();
        }
    }

    @Test
    public void test() {
        Iterator<List<String>> iterator = new WordIterator(
                new String[] {"1", "2", "3", "4"}, 3);
        // Start
        assertTrue("Before the first next() call, always has next", iterator.hasNext());
        // First page
        List<String> page = iterator.next();
        assertEquals(3, page.size());
        assertEquals("1", page.get(0));
        assertEquals("2", page.get(1));
        assertEquals("3", page.get(2));
        // Second page
        assertTrue(iterator.hasNext());
        page = iterator.next();
        assertEquals(1, page.size());
        assertEquals("4", page.get(0));
        // No more pages
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testManyPages() {
        Iterator<List<String>> iterator = new WordIterator(
                new String[] {"1", "2", "3", "4", "5", "6", "7", "8", "9"}, 1);
        assertTrue(iterator.hasNext());
        assertEquals("1", iterator.next().get(0));
        assertEquals("2", iterator.next().get(0));
        assertEquals("3", iterator.next().get(0));
        assertEquals("4", iterator.next().get(0));
        assertEquals("5", iterator.next().get(0));
        assertEquals("6", iterator.next().get(0));
        assertEquals("7", iterator.next().get(0));
        assertEquals("8", iterator.next().get(0));
        assertEquals("9", iterator.next().get(0));
        assertEquals(0, iterator.next().size());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testFullPage() {
        Iterator<List<String>> iterator = new WordIterator(
                new String[] {"1", "2", "3", "4"}, 2);
        // Start
        assertTrue("Before the first next() call, always has next", iterator.hasNext());
        // First page
        List<String> page = iterator.next();
        assertEquals(2, page.size());
        assertEquals("1", page.get(0));
        assertEquals("2", page.get(1));
        // Second page
        assertTrue(iterator.hasNext());
        page = iterator.next();
        assertEquals(2, page.size());
        assertEquals("3", page.get(0));
        assertEquals("4", page.get(1));
        // Last page
        assertTrue("The last page can be empty if the previous page is a full page", iterator.hasNext());
        page = iterator.next();
        assertEquals(0, page.size());
        // No more pages
        assertFalse(iterator.hasNext());
    }
    
    @Test
    public void testEmpy() {
        Iterator<List<String>> iterator = new WordIterator(
                new String[] {}, 2);
        // Start
        assertTrue("Before the first next() call, always has next", iterator.hasNext());
        // First page
        List<String> page = iterator.next();
        assertEquals(0, page.size());
        // No more pages
        assertFalse(iterator.hasNext());
    }

    @Test(expected=UnsupportedOperationException.class)
    public void testRemove() {
        Iterator<List<String>> iterator = new WordIterator(
                new String[] {"1", "2", "3", "4"}, 2);
        iterator.next();
        iterator.remove();
    }
}
