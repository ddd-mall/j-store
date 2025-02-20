package com.jstore.goods.domain.question;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Questionnaire {

    private AdjacencyList<Node> adjacencyList;

    public Node getNextQuestion(Set<Node> selected) {
        int selectedMaxLayer = selected.stream().mapToInt(Node::getLayer).max().orElse(Integer.MAX_VALUE);
        return selected.stream().map(adjacencyList::getConnectedNodes)
                .flatMap(Set::stream)
                .filter(node -> !selected.contains(node) &&
                        NodeType.QUESTION.equals(node.type) &&
                        node.layer == selectedMaxLayer + 1
                )
                .findAny()
                .orElse(null);
    }

    public List<Node> getNextOptions(Set<Node> selected) {
        int selectedMaxLayer = selected.stream().mapToInt(Node::getLayer).max().orElse(Integer.MAX_VALUE);
        return selected.stream().map(adjacencyList::getConnectedNodes)
                .flatMap(Set::stream)
                .filter(node -> !selected.contains(node) &&
                        NodeType.OPTION.equals(node.type) &&
                        node.layer == selectedMaxLayer + 1
                ).collect(Collectors.toList());
    }
}
