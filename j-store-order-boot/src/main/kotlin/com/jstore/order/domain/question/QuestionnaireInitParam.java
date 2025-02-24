package com.jstore.order.domain.question;

import lombok.Data;

import java.util.List;


/**
 * 问卷模型初始化参数
 */
@Data
public class QuestionnaireInitParam {

    /**
     * 问卷基本元素，问题列表
     */
    public List<Question> questions;

    /**
     * 问卷各个元素的依赖关系
     */
    public List<Dependency> dependencies;











}