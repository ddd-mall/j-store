package com.jstore.order.domain.question;

import lombok.Data;

import java.util.Set;

@Data
public class DependentFactorSet {
    private Set<DependentFactor> dependentFactors;

    /**
     * 取差集
     * @param others 另一个集合
     * @return 结果
     */
    public DependentFactorSet minus(DependentFactorSet others) {
        return null;
    }

    /**
     * 取交集
     * @param other 另一个集合
     * @return 结果
     */
    public DependentFactorSet intersect(DependentFactorSet other) {
        return null;
    }

    /**
     * 取并集
     * @param other 另一个集合
     * @return 结果
     */
    public DependentFactor union(DependentFactorSet other) {
        return null;
    }


}
