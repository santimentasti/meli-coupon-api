package com.mercadolibre.coupon.service;
import com.mercadolibre.coupon.model.Item;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

@Service
public class CouponOptimizationService {
    
    // Límites de seguridad para evitar OutOfMemoryError
    private static final int MAX_BUDGET_FOR_DP = 1_000_000; // 10,000 pesos en centavos
    private static final int MAX_ITEMS_FOR_DP = 100;
    
    /**
     * Encuentra la combinación óptima de items usando algoritmo híbrido
     * - DP para casos pequeños
     * - Greedy optimizado para casos grandes
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
        
        // Decidir algoritmo basado en el tamaño del problema
        if (validItems.size() <= MAX_ITEMS_FOR_DP && maxAmountCents <= MAX_BUDGET_FOR_DP) {
            System.out.println("Usando algoritmo DP para " + validItems.size() + " items, presupuesto: " + maxAmountCents);
            return solveKnapsack(validItems, maxAmountCents);
        } else {
            System.out.println("Usando algoritmo Greedy para " + validItems.size() + " items, presupuesto: " + maxAmountCents);
            return solveGreedy(validItems, maxAmountCents);
        }
    }
    
    /**
     * Algoritmo clásico de knapsack 0-1 con programación dinámica
     * Solo para casos pequeños
     */
    private List<String> solveKnapsack(List<ItemWithPrice> items, int maxWeight) {
        int n = items.size();
        
        try {
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
            
        } catch (OutOfMemoryError e) {
            System.err.println("DP falló por memoria, usando greedy como fallback");
            return solveGreedy(items, maxWeight);
        }
    }
    
    /**
     * Algoritmo greedy optimizado para casos grandes
     * Múltiples estrategias para maximizar el aprovechamiento del presupuesto
     */
    private List<String> solveGreedy(List<ItemWithPrice> items, int maxBudget) {
        // Ordenar por precio descendente para estrategia greedy
        List<ItemWithPrice> sortedItems = new ArrayList<>(items);
        sortedItems.sort((a, b) -> Integer.compare(b.price, a.price));
        
        List<String> bestSolution = Collections.emptyList();
        int bestValue = 0;
        
        // Estrategia 1: Greedy por precio más alto
        List<String> greedyHigh = greedyByPrice(sortedItems, maxBudget, false);
        int greedyHighValue = calculateTotalValue(greedyHigh, items);
        if (greedyHighValue > bestValue) {
            bestSolution = greedyHigh;
            bestValue = greedyHighValue;
        }
        
        // Estrategia 2: Greedy por precio más bajo (para llenar huecos)
        sortedItems.sort((a, b) -> Integer.compare(a.price, b.price));
        List<String> greedyLow = greedyByPrice(sortedItems, maxBudget, true);
        int greedyLowValue = calculateTotalValue(greedyLow, items);
        if (greedyLowValue > bestValue) {
            bestSolution = greedyLow;
            bestValue = greedyLowValue;
        }
        
        // Estrategia 3: Combinaciones de 2-3 items más caros
        if (items.size() >= 2) {
            List<String> combinationSolution = findBestSmallCombination(items, maxBudget);
            int combinationValue = calculateTotalValue(combinationSolution, items);
            if (combinationValue > bestValue) {
                bestSolution = combinationSolution;
                bestValue = combinationValue;
            }
        }
        
        System.out.println("Mejor solución greedy encontrada con valor: " + bestValue);
        return bestSolution;
    }
    
    /**
     * Greedy básico por precio
     */
    private List<String> greedyByPrice(List<ItemWithPrice> sortedItems, int maxBudget, boolean fillGaps) {
        List<String> selected = new ArrayList<>();
        int totalSpent = 0;
        
        for (ItemWithPrice item : sortedItems) {
            if (totalSpent + item.price <= maxBudget) {
                selected.add(item.id);
                totalSpent += item.price;
            }
        }
        
        return selected;
    }
    
    /**
     * Buscar las mejores combinaciones pequeñas (2-3 items)
     */
    private List<String> findBestSmallCombination(List<ItemWithPrice> items, int maxBudget) {
        List<String> bestCombination = Collections.emptyList();
        int bestValue = 0;
        
        int limit = Math.min(items.size(), 15); // Limitar para evitar explosión combinatoria
        
        // Probar todos los pares
        for (int i = 0; i < limit; i++) {
            for (int j = i + 1; j < limit; j++) {
                int totalPrice = items.get(i).price + items.get(j).price;
                if (totalPrice <= maxBudget && totalPrice > bestValue) {
                    bestValue = totalPrice;
                    bestCombination = Arrays.asList(items.get(i).id, items.get(j).id);
                }
            }
        }
        
        // Probar tríos solo si tenemos pocos items
        if (limit <= 8) {
            for (int i = 0; i < limit; i++) {
                for (int j = i + 1; j < limit; j++) {
                    for (int k = j + 1; k < limit; k++) {
                        int totalPrice = items.get(i).price + items.get(j).price + items.get(k).price;
                        if (totalPrice <= maxBudget && totalPrice > bestValue) {
                            bestValue = totalPrice;
                            bestCombination = Arrays.asList(items.get(i).id, items.get(j).id, items.get(k).id);
                        }
                    }
                }
            }
        }
        
        // Probar items individuales
        for (ItemWithPrice item : items) {
            if (item.price <= maxBudget && item.price > bestValue) {
                bestValue = item.price;
                bestCombination = Collections.singletonList(item.id);
            }
        }
        
        return bestCombination;
    }
    
    /**
     * Calcular valor total de una solución
     */
    private int calculateTotalValue(List<String> itemIds, List<ItemWithPrice> items) {
        return itemIds.stream()
                .mapToInt(id -> items.stream()
                        .filter(item -> item.id.equals(id))
                        .mapToInt(item -> item.price)
                        .findFirst()
                        .orElse(0))
                .sum();
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