package com.jstore.goods.domain.question;


import java.util.Objects;

public class Node {
    public Long id;
    public int layer;
    public int order;
    public NodeType type;
    public String content;


    public int getLayer() {
        return layer;
    }

    public int getOrder() {
        return order;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return layer == node.layer && order == node.order && Objects.equals(id, node.id) && type == node.type && Objects.equals(content, node.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, layer, order, type, content);
    }
}