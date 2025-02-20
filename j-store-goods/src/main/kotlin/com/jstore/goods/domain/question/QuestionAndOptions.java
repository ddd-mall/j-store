package com.jstore.goods.domain.question;

import java.util.List;

public class QuestionAndOptions {
    public Node question;
    public List<Node> options;

    public QuestionAndOptions setQuestion(Node question) {
        this.question = question;
        return this;
    }

    public QuestionAndOptions setOptions(List<Node> options) {
        this.options = options;
        return this;
    }
}
