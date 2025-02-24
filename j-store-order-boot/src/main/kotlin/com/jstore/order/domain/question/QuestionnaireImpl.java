package com.jstore.order.domain.question;




import java.security.InvalidParameterException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class QuestionnaireImpl implements Questionnaire {


    public QuestionnaireImpl(QuestionnaireInitParam initParam) {
        // id -> question 的映射，选项的ID也映射到题目上
        Map<Long, Question> idQuestionMap = getItemIdToQuestionMap(initParam.getQuestions());
        // 题目的ID -> 该题目所有的元素ID集合
        Map<Long, Set<Long>> questionIdToItemIdSetMap = questionIdToItemIdSet(initParam.getQuestions());
        // 每一个题目的依赖条件
        Map<Long, Set<Dependency>> dependencyMap = questionToDependencyMap(initParam, questionIdToItemIdSetMap);


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

    private Map<Long, Question> getItemIdToQuestionMap(List<Question> questions) {
        Map<Long, Question> idQuestionMap = new ConcurrentHashMap<>();
        questions.forEach(question -> {
            if (idQuestionMap.putIfAbsent(question.getId(), question) != null) {
                throw new InvalidParameterException(String.format("元素的的ID出现重复：%s ", question.getId()));
            }
            question.getOptions().forEach(option -> {
                if (idQuestionMap.putIfAbsent(option.getId(), question) != null) {
                    throw new InvalidParameterException(String.format("元素的的ID出现重复：%s ", option.getId()));
                }
            });
        });
        return idQuestionMap;
    }

    private Map<Long, Set<Long>> questionIdToItemIdSet(List<Question> questions) {
        return questions.stream().collect(Collectors.toMap(Question::getId, question -> {
            Set<Long> result = new HashSet<>();
            result.add(question.getId());
            result.addAll(question.getOptions().stream().map(Option::getId).collect(Collectors.toSet()));
            return result;
        }));
    }

    private Map<Long, Set<Dependency>> questionToDependencyMap(QuestionnaireInitParam initParam, Map<Long, Set<Long>> questionIdToItemIdSetMap) {
        return initParam.getQuestions().stream().collect(Collectors.toMap(
                Question::getId,
                question -> initParam.getDependencies().stream()
                        .filter(dependency -> dependency.isRelatedWith(questionIdToItemIdSetMap.get(question.getId())))
                        .collect(Collectors.toSet())
        ));
    }
}
