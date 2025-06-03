package com.mercadolibre.coupon.service;

import com.mercadolibre.coupon.model.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests del Servicio de Optimización de Cupones")
class CouponOptimizationServiceTest {

    @InjectMocks
    private CouponOptimizationService optimizationService;

    private List<Item> testItems;

    @BeforeEach
    void setUp() {
        testItems = Arrays.asList(
            new Item("MLA1", new BigDecimal("100.00")),
            new Item("MLA2", new BigDecimal("200.00")),
            new Item("MLA3", new BigDecimal("150.00")),
            new Item("MLA4", new BigDecimal("80.00")),
            new Item("MLA5", new BigDecimal("300.00"))
        );
    }

    @Test
    @DisplayName("Debe encontrar combinación óptima básica")
    void findOptimalItems_BasicOptimization() {
        // Given
        BigDecimal maxAmount = new BigDecimal("350.00");

        // When
        List<String> result = optimizationService.findOptimalItems(testItems, maxAmount);

        // Then
        assertThat(result).isNotEmpty();
        BigDecimal totalCost = calculateTotalCost(testItems, result);
        assertThat(totalCost).isLessThanOrEqualTo(maxAmount);
        
        // Verificar que es una solución óptima (MLA2 + MLA3 = 350.00)
        assertThat(result).containsExactlyInAnyOrder("MLA2", "MLA3");
        assertThat(totalCost).isEqualTo(new BigDecimal("350.00"));
    }

    @Test
    @DisplayName("Debe retornar lista vacía cuando no hay items")
    void findOptimalItems_EmptyList() {
        // Given
        List<Item> emptyItems = Collections.emptyList();
        BigDecimal maxAmount = new BigDecimal("500.00");

        // When
        List<String> result = optimizationService.findOptimalItems(emptyItems, maxAmount);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Debe retornar lista vacía cuando lista es null")
    void findOptimalItems_NullList() {
        // Given
        BigDecimal maxAmount = new BigDecimal("500.00");

        // When
        List<String> result = optimizationService.findOptimalItems(null, maxAmount);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Debe retornar lista vacía cuando monto es cero o negativo")
    void findOptimalItems_InvalidAmount() {
        // Test con monto cero
        List<String> result1 = optimizationService.findOptimalItems(testItems, BigDecimal.ZERO);
        assertThat(result1).isEmpty();

        // Test con monto negativo
        List<String> result2 = optimizationService.findOptimalItems(testItems, new BigDecimal("-100.00"));
        assertThat(result2).isEmpty();
    }

    @Test
    @DisplayName("Debe manejar items con precios superiores al monto máximo")
    void findOptimalItems_ItemsPriceTooHigh() {
        // Given
        List<Item> expensiveItems = Arrays.asList(
            new Item("MLA1", new BigDecimal("1000.00")),
            new Item("MLA2", new BigDecimal("2000.00"))
        );
        BigDecimal maxAmount = new BigDecimal("500.00");

        // When
        List<String> result = optimizationService.findOptimalItems(expensiveItems, maxAmount);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Debe seleccionar item único cuando coincide exactamente")
    void findOptimalItems_ExactMatch() {
        // Given
        List<Item> exactItems = Arrays.asList(
            new Item("MLA1", new BigDecimal("100.00")),
            new Item("MLA2", new BigDecimal("500.00")), // Coincide exactamente
            new Item("MLA3", new BigDecimal("300.00"))
        );
        BigDecimal maxAmount = new BigDecimal("500.00");

        // When
        List<String> result = optimizationService.findOptimalItems(exactItems, maxAmount);

        // Then
        assertThat(result).containsExactly("MLA2");
        assertThat(calculateTotalCost(exactItems, result)).isEqualTo(maxAmount);
    }

    @Test
    @DisplayName("Debe optimizar con precios decimales")
    void findOptimalItems_DecimalPrices() {
        // Given
        List<Item> decimalItems = Arrays.asList(
            new Item("MLA1", new BigDecimal("99.99")),
            new Item("MLA2", new BigDecimal("149.50")),
            new Item("MLA3", new BigDecimal("75.25")),
            new Item("MLA4", new BigDecimal("200.75"))
        );
        BigDecimal maxAmount = new BigDecimal("300.00");

        // When
        List<String> result = optimizationService.findOptimalItems(decimalItems, maxAmount);

        // Then
        assertThat(result).isNotEmpty();
        BigDecimal totalCost = calculateTotalCost(decimalItems, result);
        assertThat(totalCost).isLessThanOrEqualTo(maxAmount);
        
        // Verificar que maximiza el valor (MLA1 + MLA2 + MLA3 = 324.74 > 300, so MLA2 + MLA4 = 350.25 > 300)
        // La mejor combinación debería ser MLA1 + MLA2 = 249.49
        assertThat(result).containsExactlyInAnyOrder("MLA1", "MLA2");
    }

    @Test
    @DisplayName("Debe manejar items con precio cero o negativo")
    void findOptimalItems_InvalidItemPrices() {
        // Given
        List<Item> invalidItems = Arrays.asList(
            new Item("MLA1", new BigDecimal("100.00")),
            new Item("MLA2", BigDecimal.ZERO),
            new Item("MLA3", new BigDecimal("-50.00")),
            new Item("MLA4", new BigDecimal("200.00"))
        );
        BigDecimal maxAmount = new BigDecimal("250.00");

        // When
        List<String> result = optimizationService.findOptimalItems(invalidItems, maxAmount);

        // Then
        assertThat(result).containsExactlyInAnyOrder("MLA1", "MLA4");
        assertThat(calculateTotalCost(invalidItems, result)).isEqualTo(new BigDecimal("300.00"));
    }

    @Test
    @DisplayName("Debe encontrar múltiples items que maximizan el valor")
    void findOptimalItems_MultipleItemsOptimal() {
        // Given
        List<Item> multipleItems = Arrays.asList(
            new Item("MLA1", new BigDecimal("50.00")),
            new Item("MLA2", new BigDecimal("60.00")),
            new Item("MLA3", new BigDecimal("70.00")),
            new Item("MLA4", new BigDecimal("80.00")),
            new Item("MLA5", new BigDecimal("90.00"))
        );
        BigDecimal maxAmount = new BigDecimal("200.00");

        // When
        List<String> result = optimizationService.findOptimalItems(multipleItems, maxAmount);

        // Then
        assertThat(result).isNotEmpty();
        BigDecimal totalCost = calculateTotalCost(multipleItems, result);
        assertThat(totalCost).isLessThanOrEqualTo(maxAmount);
        
        // Verificar que es una combinación óptima
        assertThat(totalCost).isEqualTo(new BigDecimal("200.00")); // MLA2 + MLA4 + MLA2 o similar
    }

    @Test
    @DisplayName("Debe manejar performance con lista grande de items")
    void findOptimalItems_PerformanceTest() {
        // Given
        List<Item> largeItemList = IntStream.range(0, 1000)
            .mapToObj(i -> new Item("MLA" + i, new BigDecimal(String.valueOf(i + 1))))
            .toList();
        BigDecimal maxAmount = new BigDecimal("5000.00");

        // When
        long startTime = System.currentTimeMillis();
        List<String> result = optimizationService.findOptimalItems(largeItemList, maxAmount);
        long executionTime = System.currentTimeMillis() - startTime;

        // Then
        assertThat(result).isNotEmpty();
        assertThat(executionTime).isLessThan(5000); // Debe completarse en menos de 5 segundos
        
        BigDecimal totalCost = calculateTotalCost(largeItemList, result);
        assertThat(totalCost).isLessThanOrEqualTo(maxAmount);
    }

    @Test
    @DisplayName("Debe manejar caso límite con un solo item")
    void findOptimalItems_SingleItem() {
        // Given
        List<Item> singleItem = Arrays.asList(new Item("MLA1", new BigDecimal("100.00")));
        BigDecimal maxAmount = new BigDecimal("150.00");

        // When
        List<String> result = optimizationService.findOptimalItems(singleItem, maxAmount);

        // Then
        assertThat(result).containsExactly("MLA1");
    }

    @Test
    @DisplayName("Debe manejar caso límite con un solo item muy caro")
    void findOptimalItems_SingleExpensiveItem() {
        // Given
        List<Item> singleExpensiveItem = Arrays.asList(new Item("MLA1", new BigDecimal("1000.00")));
        BigDecimal maxAmount = new BigDecimal("150.00");

        // When
        List<String> result = optimizationService.findOptimalItems(singleExpensiveItem, maxAmount);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Debe optimizar correctamente con conversión a centavos")
    void findOptimalItems_CentavosConversion() {
        // Given - Precios que podrían causar problemas de precisión decimal
        List<Item> precisionItems = Arrays.asList(
            new Item("MLA1", new BigDecimal("33.33")),
            new Item("MLA2", new BigDecimal("66.67")),
            new Item("MLA3", new BigDecimal("99.99"))
        );
        BigDecimal maxAmount = new BigDecimal("100.00");

        // When
        List<String> result = optimizationService.findOptimalItems(precisionItems, maxAmount);

        // Then
        assertThat(result).isNotEmpty();
        BigDecimal totalCost = calculateTotalCost(precisionItems, result);
        assertThat(totalCost).isLessThanOrEqualTo(maxAmount);
        
        // Debería seleccionar MLA3 (99.99) como la mejor opción individual
        assertThat(result).containsExactly("MLA3");
    }

    @Test
    @DisplayName("Debe garantizar solución óptima en caso conocido")
    void findOptimalItems_KnownOptimalCase() {
        // Given - Caso clásico de knapsack
        List<Item> knapsackItems = Arrays.asList(
            new Item("MLA1", new BigDecimal("10.00")),  // peso 10, valor 10
            new Item("MLA2", new BigDecimal("20.00")),  // peso 20, valor 20  
            new Item("MLA3", new BigDecimal("30.00"))   // peso 30, valor 30
        );
        BigDecimal maxAmount = new BigDecimal("50.00");

        // When
        List<String> result = optimizationService.findOptimalItems(knapsackItems, maxAmount);

        // Then
        assertThat(result).containsExactlyInAnyOrder("MLA2", "MLA3");
        assertThat(calculateTotalCost(knapsackItems, result)).isEqualTo(new BigDecimal("50.00"));
    }

    /**
     * Método auxiliar para calcular el costo total de los items seleccionados
     */
    private BigDecimal calculateTotalCost(List<Item> items, List<String> selectedIds) {
        return items.stream()
            .filter(item -> selectedIds.contains(item.getId()))
            .map(Item::getPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
