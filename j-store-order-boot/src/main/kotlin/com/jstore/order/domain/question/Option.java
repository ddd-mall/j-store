package com.jstore.order.domain.question;

import lombok.Data;

@Data
public class Option {
    public Long id;
    public String key;
    public String title;
    public String subTitle;
    public String img;
}