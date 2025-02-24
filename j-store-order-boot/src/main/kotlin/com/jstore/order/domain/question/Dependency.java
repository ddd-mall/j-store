package com.jstore.order.domain.question;

import lombok.Data;

import java.util.Collection;

/**
 * 元素的依赖因子，
 */
@Data
public class Dependency {
    /**
     * 元素的ID
     */
    private Long itemId;

    /**
     * 该元素的依赖条件，展开后是以若干个集合为单元的集合，如 { (a1), (a2, a3), (a4) } 代表表达式 a1 || (a2 && a3) || a4
     * <pre style = "code">
     *     contains(a1) || (contains(a2) && contains(a3)) || contains(a4)
     * </pre>
     */
    private final DependentFactorSet<Long> dependentFactorSet;

    public Dependency(Long itemId, DependentFactorSet<Long> dependentFactorSet) {
        this.itemId = itemId;
        this.dependentFactorSet = dependentFactorSet;
    }

    /**
     * 判断当前依赖关系与一组给定的元素ID是否相关
     * @param itemIds 一组元素ID
     * @return 判断结果
     */
    public boolean isRelatedWith(Collection<Long>  itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return false;
        }

        for (DependentFactor<Long> dependentFactor : dependentFactorSet.getDependentFactors()) {
            for (Long itemId : itemIds) {
                if (dependentFactor.getItemIdSet().contains(itemId)) {
                    return true;
                }
            }
        }
        return false;
    }
}