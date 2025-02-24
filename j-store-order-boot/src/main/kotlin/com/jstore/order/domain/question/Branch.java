package com.jstore.order.domain.question;

import lombok.Data;

@Data
public class Branch {

    private DependentFactorSet<Long>  dependentFactorSet;
}
