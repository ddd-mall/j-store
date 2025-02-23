package com.jstore.order.domain.question;



import com.jstore.order.domain.question.QuestionnaireInitParam.Question;

import java.util.Collection;

public class QuestionnaireImpl implements Questionnaire{


    public QuestionnaireImpl(QuestionnaireInitParam initParam) {

    }


    @Override
    public Question root() {
        return null;
    }

    @Override
    public Question next(Collection<Long> selectedIds) {
        return null;
    }

    @Override
    public boolean hasNext(Collection<Long> selectedIds) {
        return false;
    }
}
