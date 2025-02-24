package com.jstore.order.domain.question;

import lombok.Data;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Data
public class DependentFactorSet<T> {
    private final Set<DependentFactor<T>> dependentFactors;

    public DependentFactorSet(DependentFactorSet<T> others) {
        this.dependentFactors = new HashSet<>(others.dependentFactors);
    }

    public DependentFactorSet(Collection<DependentFactor<T>> datas) {
        dependentFactors = new HashSet<>();
        if (datas == null) {
            return;
        }
        dependentFactors.addAll(datas);
    }

    /**
     * 取差集 当前集合的元素减去存在于另一个集合的元素
     * @param others 另一个集合
     * @return 结果
     */
    public DependentFactorSet<T> minus(DependentFactorSet<T> others) {
        DependentFactorSet<T> result = new DependentFactorSet<>(this);
        result.dependentFactors.removeAll(others.dependentFactors);
        return result;
    }

    /**
     * 取交集 取两个集合中的公共元素
     * @param other 另一个集合
     * @return 结果
     */
    public DependentFactorSet<T> intersect(DependentFactorSet<T> other) {
        DependentFactorSet<T> result = new DependentFactorSet<>(this);
        result.dependentFactors.retainAll(other.dependentFactors);
        return result;
    }

    /**
     * 取并集 合并两个集合
     * @param other 另一个集合
     * @return 结果
     */
    public DependentFactorSet<T> union(DependentFactorSet<T> other) {
        DependentFactorSet<T> result = new DependentFactorSet<>(this);
        result.dependentFactors.addAll(other.dependentFactors);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof DependentFactorSet)) return false;

        DependentFactorSet<?> that = (DependentFactorSet<?>) o;

        return new EqualsBuilder().append(dependentFactors, that.dependentFactors).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(dependentFactors).toHashCode();
    }
}
