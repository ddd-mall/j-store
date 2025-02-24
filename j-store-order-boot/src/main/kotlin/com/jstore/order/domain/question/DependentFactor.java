package com.jstore.order.domain.question;

import lombok.Data;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Data
public class DependentFactor<T> {
    private final Set<T> itemIdSet;

    public DependentFactor(Set<T> itemIdSet) {
        Objects.requireNonNull(itemIdSet);
        this.itemIdSet = itemIdSet;
    }

    public  DependentFactor(Collection<T> data) {
        this();
        this.itemIdSet.addAll(data);
    }

    public  DependentFactor() {
        this.itemIdSet = new HashSet<>();
    }


    public boolean add(T item) {
        return this.itemIdSet.add(item);
    }


    public int size() {
        return itemIdSet.size();
    }

    /**
     * 判断这个集合是否时另一个集合的子集
     *
     * @param other 另一个集合
     * @return 判断结果
     */
    public boolean in(DependentFactor<T> other) {
        if (other == null) {
            return false;
        }
        return other.itemIdSet.containsAll(itemIdSet);
    }

    public boolean contain(DependentFactor<T> other) {
        if (null == other || other.itemIdSet.isEmpty()) {
            return true;
        }
        return this.itemIdSet.containsAll(other.itemIdSet);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof DependentFactor)) return false;

        DependentFactor<?> that = (DependentFactor<?>) o;
        return this.itemIdSet.equals(that.itemIdSet);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(itemIdSet).toHashCode();
    }

}
