package com.mercadolibre.coupon.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

public class CouponRequest {
    
    @JsonProperty("item_ids")  // Esta línea mapea el JSON snake_case al Java camelCase
    @NotEmpty(message = "La lista de Item_id no puede estar vacia")
    private List<String> itemIds;
    
    @NotNull(message = "La cantidad es nula")
    @Positive(message = "La cantidad debe ser un numero positivo")
    private BigDecimal amount;
    
    public CouponRequest() {}
    
    public CouponRequest(List<String> itemIds, BigDecimal amount) {
        this.itemIds = itemIds;
        this.amount = amount;
    }
    
    public List<String> getItemIds() { return itemIds; }
    public void setItemIds(List<String> itemIds) { this.itemIds = itemIds; }
    
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
