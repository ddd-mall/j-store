package com.jstore.order.domain.question;

import lombok.Data;

import java.util.List;

@Data
public class Question {
    public Long id;
    public String title;
    public String subTitle;
    public String img;
    public QuestionType type;
    public Integer min;
    public Integer max;
    public List<Option> options;

    public boolean valid() {
        if (QuestionType.MULTIPLE_CHOICE.equals(type)) {
            return 0 < min && min <= max && max <= options.size();
        }
        return true;
    }


}