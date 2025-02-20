package com.jstore.goods.domain.question;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AdjacencyList<T> {
    private final Map<T, Set<T>> connectionMap = new HashMap<>();

    public Set<T> getConnectedNodes(T current) {
        return connectionMap.get(current);
    }

    public void connect(T a, T b) {
        Set<T> alreadyExist;

        if ((alreadyExist = connectionMap.putIfAbsent(a, new HashSet<>(Collections.singleton(b)))) != null) {
            alreadyExist.add(b);
        }

        if ((alreadyExist = connectionMap.putIfAbsent(b, new HashSet<>(Collections.singleton(a)))) != null) {
            alreadyExist.add(a);
        }
    }

    public void onewayConnect(T a, T b) {
        Set<T> alreadyExist;
        if ((alreadyExist = connectionMap.putIfAbsent(a, new HashSet<>(Collections.singleton(b)))) != null) {
            alreadyExist.add(b);
        }
    }

    public static void main(String[] args) {
        AdjacencyList<String> adjacencyList = new AdjacencyList<>();


        String a = "A";
        String b = "B";
        String c = "C";
        adjacencyList.connect(a, b);
        adjacencyList.connect(a, c);
        adjacencyList.connect(b, c);

        adjacencyList.getConnectedNodes(c).forEach(System.out::println);
    }

}