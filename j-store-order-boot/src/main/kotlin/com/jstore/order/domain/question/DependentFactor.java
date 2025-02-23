package com.jstore.order.domain.question;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class DependentFactor {
    private Set<Long> itemIdSet = new HashSet<>();

    public boolean in(DependentFactor other) {
        if (other == null || other.itemIdSet == null) {
            return false;
        }
        return other.itemIdSet.containsAll(itemIdSet);
    }

    public boolean contain(DependentFactor other) {
        if (null == other || other.itemIdSet == null || other.itemIdSet.isEmpty()) {
            return true;
        }
        return this.itemIdSet.containsAll(other.itemIdSet);
    }

}
