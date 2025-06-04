package com.mercadolibre.coupon.service;
import com.mercadolibre.coupon.model.Item;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

@Service
public class CouponOptimizationService {
    
    /**
     * Encuentra la combinación óptima de items usando algoritmo de knapsack (0-1)
     * Maximiza el valor total sin exceder el monto máximo del cupón
     */
    public List<String> findOptimalItems(List<Item> items, BigDecimal maxAmount) {
        if (items == null || items.isEmpty() || maxAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return Collections.emptyList();
        }
        
        // Convertir a centavos para evitar problemas de precisión decimal
        int maxAmountCents = maxAmount.multiply(BigDecimal.valueOf(100)).intValue();
        
        // Filtrar items válidos y convertir a centavos
        List<ItemWithPrice> validItems = new ArrayList<>();
        for (Item item : items) {
            int priceCents = item.getPrice().multiply(BigDecimal.valueOf(100)).intValue();
            if (priceCents > 0 && priceCents <= maxAmountCents) {
                validItems.add(new ItemWithPrice(item.getId(), priceCents));
            }
        }
        
        if (validItems.isEmpty()) {
            return Collections.emptyList();
        }
        
        return solveKnapsack(validItems, maxAmountCents);
    }
    
    /**
     * Algoritmo clásico de knapsack 0-1 con programación dinámica
     */
    private List<String> solveKnapsack(List<ItemWithPrice> items, int maxWeight) {
        int n = items.size();
        
        // DP table: dp[i][w] = maximum value using first i items with weight limit w
        int[][] dp = new int[n + 1][maxWeight + 1];
        
        // Llenar tabla DP
        for (int i = 1; i <= n; i++) {
            ItemWithPrice currentItem = items.get(i - 1);
            for (int w = 1; w <= maxWeight; w++) {
                // No tomar el item actual
                dp[i][w] = dp[i - 1][w];
                
                // Tomar el item actual si es posible
                if (currentItem.price <= w) {
                    int valueWithItem = dp[i - 1][w - currentItem.price] + currentItem.price;
                    dp[i][w] = Math.max(dp[i][w], valueWithItem);
                }
            }
        }
        
        // Backtrack para encontrar items seleccionados
        List<String> selectedItems = new ArrayList<>();
        int w = maxWeight;
        for (int i = n; i > 0 && w > 0; i--) {
            if (dp[i][w] != dp[i - 1][w]) {
                ItemWithPrice item = items.get(i - 1);
                selectedItems.add(item.id);
                w -= item.price;
            }
        }
        
        Collections.sort(selectedItems);
        
        return selectedItems;
    }
    
    private static class ItemWithPrice {
        final String id;
        final int price;
        
        ItemWithPrice(String id, int price) {
            this.id = id;
            this.price = price;
        }
    }
}
