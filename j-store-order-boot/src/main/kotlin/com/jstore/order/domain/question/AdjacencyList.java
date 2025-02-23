package com.jstore.order.domain.question;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AdjacencyList<T> {
    private final Map<T, Set<T>> connectionMap = new HashMap<>();

    public Set<T> getConnectedNodes(T current) {
        return connectionMap.get(current);
    }

    public Set<T> getConnectedNodes(Collection<T> from) {
         return from.stream().map(connectionMap::get).flatMap(Set::stream).collect(Collectors.toSet());
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

    public Set<T> getConnectedExcludeSelf(Collection<T> selected) {
        Set<T> connectedNode = getConnectedNodes(selected);
        connectedNode.removeAll(selected);
        return connectedNode;
    }

}