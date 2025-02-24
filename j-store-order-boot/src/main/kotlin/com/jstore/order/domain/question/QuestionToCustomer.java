package com.jstore.order.domain.question;

import lombok.Data;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * C端展示的形式
 */
@Data
public class QuestionToCustomer {
    private Long id;
    private String title;
    private String subTitle;
    private String content;
    private List<OptionToCustomer> options;
    private final Map<DependentFactor<Long>, QuestionToCustomer> questionBranchMap = new ConcurrentHashMap<>();


    public QuestionToCustomer next(Collection<Long> currentSelected) {
        DependentFactor<Long> currentFactor = new DependentFactor<>(currentSelected);
        for (DependentFactor<Long> mapKey : questionBranchMap.keySet()) {
            if (currentFactor.contain(mapKey)) {
                return questionBranchMap.get(mapKey);
            }
        }
        return null;
    }
}
