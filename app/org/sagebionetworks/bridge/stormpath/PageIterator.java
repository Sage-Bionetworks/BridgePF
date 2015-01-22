package org.sagebionetworks.bridge.stormpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public abstract class PageIterator<T> implements Iterator<List<T>>, Iterable<List<T>> {

    private int pageStart = 0;
    private boolean hasNext = true;

    @Override
    public final boolean hasNext() {
        return hasNext;
    }

    @Override
    public final List<T> next() {
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
    public final void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public final Iterator<List<T>> iterator() {
        return this;
    }

    public final int pageStart() {
        return pageStart;
    }

    public abstract int pageSize();

    public abstract Iterator<T> nextPage();
}
