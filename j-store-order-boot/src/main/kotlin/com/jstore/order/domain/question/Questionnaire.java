package com.jstore.order.domain.question;



import com.jstore.order.domain.question.QuestionnaireInitParam.Question;

import java.util.Collection;

/**
 * 问卷调查
 */
interface Questionnaire {
    Question root();
    Question next(Collection<Long> selectedIds);
    boolean hasNext(Collection<Long> selectedIds);
}