package com.jstore.goods.domain.question;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Questionnaire {

    private final AdjacencyList<Node> adjacencyList;

    public Questionnaire() {
        this.adjacencyList = new AdjacencyList<>();
    }

    public QuestionAndOptions getNextLayer(Collection<Node> currentSelected) {
        int nextLayer = currentSelected.stream().mapToInt(Node::getLayer).max().orElse(Integer.MAX_VALUE - 1) + 1;
        Set<Node> nextLayerNodes = getConnectedNodeExcludeSelf(currentSelected);

        Node question = nextLayerNodes.stream()
                .filter(node -> NodeType.QUESTION.equals(node.type) && nextLayer == node.layer)
                .findFirst()
                .orElse(null);

        List<Node> options = nextLayerNodes.stream()
                .filter(node -> NodeType.OPTION.equals(node.type) && nextLayer == node.layer)
                .sorted(Comparator.comparing(Node::getOrder))
                .collect(Collectors.toList());


        return new QuestionAndOptions().setQuestion(question).setOptions(options);
    }

    private Set<Node> getConnectedNodeExcludeSelf(Collection<Node> selected) {
        Set<Node> connectedNode = adjacencyList.getConnectedNodes(selected);
        connectedNode.removeAll(selected);
        return connectedNode;
    }

    public static void main(String[] args) {
        AdjacencyList<String> adjacencyList = new AdjacencyList<>();


        String a = "A";
        String b = "B";
        String c = "C";
        adjacencyList.connect(a, b);
        adjacencyList.connect(a, c);
        adjacencyList.connect(b, c);

        Set<String> strings = Stream.of(a, b).map(adjacencyList::getConnectedNodes).reduce((i, j) -> {
            i.addAll(j);
            return i;
        }).orElse(Collections.emptySet());
        strings.removeAll(Arrays.asList(a, b));
        strings.forEach(System.out::println);
    }
}
