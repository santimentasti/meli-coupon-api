package com.mercadolibre.coupon.model;

import java.math.BigDecimal;

public class Item {
    private String id;
    private BigDecimal price;
    
    public Item(String id, BigDecimal price) {
        this.id = id;
        this.price = price;
    }
    
    public String getId() { return id; }
    public BigDecimal getPrice() { return price; }
}
