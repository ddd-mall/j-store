package com.jstore.order.domain.question;

import lombok.Data;

import java.util.List;


@Data
public class QuestionnaireInitParam {

    public List<Question> questions;

    public List<Dependency> dependencies;

    @Data
    public static class Question {
        public Long id;
        public String title;
        public String subTitle;
        public String img;
        public Integer min;
        public Integer max;
        public List<Option> options;

        public boolean valid() {
            return 0 < min && min <= max && max <= options.size();
        }
    }


    @Data
    public static class Option {
        public Long id;
        public String key;
        public String title;
        public String subTitle;
        public String img;
        public String content;
    }


    /**
     * 元素的依赖因子，
     */
    @Data
    public static class Dependency {
        private Long itemId;
        private DependentFactorSet dependentFactorSet;


    }



}