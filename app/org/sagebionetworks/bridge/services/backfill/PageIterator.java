package org.sagebionetworks.bridge.services.backfill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Iterates page by page. A page is a list of items.
 * That is the page is of known size and the items are in order.
 */
abstract class PageIterator<T> implements Iterator<List<T>> {

    private int pageStart = 0;
    private boolean hasNext = true;

    @Override
    public boolean hasNext() {
        return hasNext;
    }

    @Override
    public List<T> next() {
        if (!hasNext) {
            return Collections.emptyList();
        }
        final int pageSize = pageSize();
        Iterator<T> iterator = nextPage();
        List<T> page = new ArrayList<>(pageSize);
        while (iterator.hasNext()) {
            page.add(iterator.next());
        }
        hasNext = page.size() == pageSize;
        pageStart += pageSize;
        return Collections.unmodifiableList(page);
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    int pageStart() {
        return pageStart;
    }

    abstract int pageSize();

    abstract Iterator<T> nextPage();
}
