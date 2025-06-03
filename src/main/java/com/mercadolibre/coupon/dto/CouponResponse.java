package com.mercadolibre.coupon.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public class CouponResponse {
    @JsonProperty("item_ids")
    private List<String> itemIds;
    
    private BigDecimal total;
    
    public CouponResponse() {}
    
    public CouponResponse(List<String> itemIds, BigDecimal total) {
        this.itemIds = itemIds;
        this.total = total;
    }
    
    public List<String> getItemIds() { return itemIds; }
    public void setItemIds(List<String> itemIds) { this.itemIds = itemIds; }
    
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
}