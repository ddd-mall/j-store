package com.jstore.order.domain.question;


import lombok.Getter;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;


public class Node<T> {
    public Long id;
    @Getter
    public int layer;
    @Getter
    public int order;
    public NodeType type;

    public T content;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof Node)) return false;

        Node<?> node = (Node<?>) o;

        return new EqualsBuilder().append(layer, node.layer).append(order, node.order).append(id, node.id).append(type, node.type).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(id).append(layer).append(order).append(type).toHashCode();
    }
}