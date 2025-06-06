package com.mercadolibre.coupon.service;

import com.mercadolibre.coupon.model.Item;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CouponOptimizationService {
    
    // Límites más agresivos pero seguros
    private static final int MAX_BUDGET_FOR_DP = 10_000_000; // 100,000 pesos
    private static final int MAX_ITEMS_FOR_DP = 2000;
    private static final long MAX_DP_OPERATIONS = 500_000_000L; // 500M operaciones
    
    public List<String> findOptimalItems(List<Item> items, BigDecimal maxAmount) {
        if (items == null || items.isEmpty() || maxAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return Collections.emptyList();
        }
        
        int maxAmountCents = maxAmount.multiply(BigDecimal.valueOf(100)).intValue();
        
        // Filtrar y convertir items válidos
        List<ItemWithPrice> validItems = items.stream()
            .filter(item -> item.getPrice().compareTo(BigDecimal.ZERO) > 0 && 
                           item.getPrice().compareTo(maxAmount) <= 0)
            .map(item -> new ItemWithPrice(
                item.getId(), 
                item.getPrice().multiply(BigDecimal.valueOf(100)).intValue()
            ))
            .collect(Collectors.toList());
        
        if (validItems.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Decisión de algoritmo más sofisticada
        long estimatedOperations = (long) validItems.size() * maxAmountCents;
        
        if (validItems.size() <= MAX_ITEMS_FOR_DP && 
            maxAmountCents <= MAX_BUDGET_FOR_DP && 
            estimatedOperations <= MAX_DP_OPERATIONS) {
            
            System.out.println("Usando DP optimizado para " + validItems.size() + " items");
            return solveOptimizedKnapsack(validItems, maxAmountCents);
        } else {
            System.out.println("Usando algoritmo híbrido greedy para " + validItems.size() + " items");
            return solveHybridGreedy(validItems, maxAmountCents);
        }
    }
    
    /**
     * Knapsack 0-1 con optimizaciones de memoria
     */
    private List<String> solveOptimizedKnapsack(List<ItemWithPrice> items, int maxWeight) {
        int n = items.size();
        
        try {
            // Optimización: usar solo dos filas en lugar de matriz completa
            int[] prev = new int[maxWeight + 1];
            int[] curr = new int[maxWeight + 1];
            
            // Para reconstruir la solución, necesitamos guardar las decisiones
            boolean[][] keep = new boolean[n][maxWeight + 1];
            
            for (int i = 0; i < n; i++) {
                ItemWithPrice item = items.get(i);
                
                for (int w = 0; w <= maxWeight; w++) {
                    // No tomar el item
                    curr[w] = prev[w];
                    
                    // Considerar tomar el item
                    if (item.price <= w) {
                        int valueWithItem = prev[w - item.price] + item.price;
                        if (valueWithItem > curr[w]) {
                            curr[w] = valueWithItem;
                            keep[i][w] = true;
                        }
                    }
                }
                
                // Intercambiar arrays
                int[] temp = prev;
                prev = curr;
                curr = temp;
            }
            
            // Reconstruir solución
            List<String> result = new ArrayList<>();
            int w = maxWeight;
            
            for (int i = n - 1; i >= 0 && w > 0; i--) {
                if (keep[i][w]) {
                    result.add(items.get(i).id);
                    w -= items.get(i).price;
                }
            }
            
            Collections.sort(result);
            return result;
            
        } catch (OutOfMemoryError e) {
            System.err.println("DP falló por memoria, usando greedy híbrido");
            return solveHybridGreedy(items, maxWeight);
        }
    }
    
    /**
     * Algoritmo greedy híbrido más sofisticado
     */
    private List<String> solveHybridGreedy(List<ItemWithPrice> items, int maxBudget) {
        List<String> bestSolution = Collections.emptyList();
        int bestValue = 0;
        
        // Estrategia 1: Greedy por eficiencia (precio/peso = 1 en este caso)
        List<String> greedyEfficient = greedyByEfficiency(items, maxBudget);
        int efficientValue = calculateTotalValue(greedyEfficient, items);
        if (efficientValue > bestValue) {
            bestSolution = greedyEfficient;
            bestValue = efficientValue;
        }
        
        // Estrategia 2: Greedy por precio descendente
        List<String> greedyHigh = greedyByPrice(items, maxBudget, true);
        int highValue = calculateTotalValue(greedyHigh, items);
        if (highValue > bestValue) {
            bestSolution = greedyHigh;
            bestValue = highValue;
        }
        
        // Estrategia 3: Greedy por precio ascendente (llenar huecos)
        List<String> greedyLow = greedyByPrice(items, maxBudget, false);
        int lowValue = calculateTotalValue(greedyLow, items);
        if (lowValue > bestValue) {
            bestSolution = greedyLow;
            bestValue = lowValue;
        }
        
        // Estrategia 4: Branch and bound limitado para items más caros
        if (items.size() <= 50) {
            List<String> branchBoundSolution = limitedBranchAndBound(items, maxBudget);
            int bbValue = calculateTotalValue(branchBoundSolution, items);
            if (bbValue > bestValue) {
                bestSolution = branchBoundSolution;
                bestValue = bbValue;
            }
        }
        
        // Estrategia 5: Combinaciones inteligentes
        List<String> smartCombination = findSmartCombinations(items, maxBudget);
        int smartValue = calculateTotalValue(smartCombination, items);
        if (smartValue > bestValue) {
            bestSolution = smartCombination;
            bestValue = smartValue;
        }
        
        System.out.println("Mejor solución híbrida con valor: " + bestValue);
        return bestSolution;
    }
    
    private List<String> greedyByEfficiency(List<ItemWithPrice> items, int maxBudget) {
        // Para este problema, la eficiencia es simplemente el precio
        // porque valor = precio y peso = precio
        return greedyByPrice(items, maxBudget, true);
    }
    
    private List<String> greedyByPrice(List<ItemWithPrice> items, int maxBudget, boolean descending) {
        List<ItemWithPrice> sorted = new ArrayList<>(items);
        if (descending) {
            sorted.sort((a, b) -> Integer.compare(b.price, a.price));
        } else {
            sorted.sort((a, b) -> Integer.compare(a.price, b.price));
        }
        
        List<String> selected = new ArrayList<>();
        int totalSpent = 0;
        
        for (ItemWithPrice item : sorted) {
            if (totalSpent + item.price <= maxBudget) {
                selected.add(item.id);
                totalSpent += item.price;
            }
        }
        
        return selected;
    }
    
    /**
     * Branch and bound limitado para casos medianos
     */
    private List<String> limitedBranchAndBound(List<ItemWithPrice> items, int maxBudget) {
        // Ordenar por precio descendente para mejor poda
        List<ItemWithPrice> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> Integer.compare(b.price, a.price));
        
        BranchBoundResult result = new BranchBoundResult();
        branchBound(sorted, 0, 0, new ArrayList<>(), maxBudget, result, 0);
        
        return result.bestSolution;
    }
    
    private void branchBound(List<ItemWithPrice> items, int index, int currentValue, 
                           List<String> currentSolution, int remainingBudget, 
                           BranchBoundResult result, int depth) {
        
        // Limitar profundidad para evitar explosión
        if (depth > 1000000) return;
        
        if (currentValue > result.bestValue) {
            result.bestValue = currentValue;
            result.bestSolution = new ArrayList<>(currentSolution);
        }
        
        if (index >= items.size()) return;
        
        // Poda: si el bound superior no puede mejorar, no continuar
        int upperBound = currentValue + getRemainingBound(items, index, remainingBudget);
        if (upperBound <= result.bestValue) return;
        
        // No tomar el item actual
        branchBound(items, index + 1, currentValue, currentSolution, 
                   remainingBudget, result, depth + 1);
        
        // Tomar el item actual si es posible
        ItemWithPrice item = items.get(index);
        if (item.price <= remainingBudget) {
            currentSolution.add(item.id);
            branchBound(items, index + 1, currentValue + item.price, currentSolution,
                       remainingBudget - item.price, result, depth + 1);
            currentSolution.remove(currentSolution.size() - 1);
        }
    }
    
    private int getRemainingBound(List<ItemWithPrice> items, int startIndex, int budget) {
        int bound = 0;
        for (int i = startIndex; i < items.size() && budget > 0; i++) {
            if (items.get(i).price <= budget) {
                bound += items.get(i).price;
                budget -= items.get(i).price;
            }
        }
        return bound;
    }
    
    /**
     * Combinaciones inteligentes basadas en patrones comunes
     */
    private List<String> findSmartCombinations(List<ItemWithPrice> items, int maxBudget) {
        List<ItemWithPrice> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> Integer.compare(b.price, a.price));
        
        List<String> bestCombination = Collections.emptyList();
        int bestValue = 0;
        
        int limit = Math.min(sorted.size(), 20);
        
        // Probar el item más caro + combinaciones del resto
        for (int i = 0; i < limit; i++) {
            ItemWithPrice mainItem = sorted.get(i);
            if (mainItem.price > maxBudget) continue;
            
            int remainingBudget = maxBudget - mainItem.price;
            List<String> subSolution = greedyByPrice(
                sorted.subList(i + 1, sorted.size()), 
                remainingBudget, 
                false
            );
            subSolution.add(mainItem.id);
            
            int totalValue = calculateTotalValue(subSolution, sorted);
            if (totalValue > bestValue) {
                bestValue = totalValue;
                bestCombination = subSolution;
            }
        }
        
        return bestCombination;
    }
    
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
    
    private static class BranchBoundResult {
        List<String> bestSolution = Collections.emptyList();
        int bestValue = 0;
    }
}