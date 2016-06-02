package org.sagebionetworks.bridge.models;

import java.util.List;

import org.joda.time.LocalDate;

public class DateRangeResourceList<T> {
    
    private final List<T> items;
    private final LocalDate startDate;
    private final LocalDate endDate;

    public DateRangeResourceList(List<T> items, LocalDate startDate, LocalDate endDate) {
        this.items = items;
        this.startDate = startDate;
        this.endDate = endDate;
    }
    public List<T> getItems() {
        return items;
    }
    public int getTotal() {
        return items.size();
    }
    public LocalDate getStartDate() {
        return startDate;
    }
    public LocalDate getEndDate() {
        return endDate;
    }
    
}
