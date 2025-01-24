package au.org.aodn.oceancurrent.constant;

import co.elastic.clients.elasticsearch._types.SortOrder;

public enum DateDirection {
    BEFORE, AFTER;

    public SortOrder getSortOrder() {
        return this == BEFORE ? SortOrder.Desc : SortOrder.Asc;
    }
}
