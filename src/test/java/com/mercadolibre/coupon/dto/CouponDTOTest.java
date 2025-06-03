package com.mercadolibre.coupon.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadolibre.coupon.controller.CouponController;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("Tests del Controlador de Cupones")
class CouponDTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testCouponRequestValidation() {
        // Test con datos válidos
        List<String> itemIds = Arrays.asList("MLA1", "MLA2", "MLA3");
        BigDecimal amount = new BigDecimal("500.00");
        
        CouponRequest request = new CouponRequest();
        request.setItemIds(itemIds);
        request.setAmount(amount);
        
        assertThat(request.getItemIds()).isEqualTo(itemIds);
        assertThat(request.getAmount()).isEqualTo(amount);
    }

    @Test
    void testCouponRequestSerialization() throws Exception {
        // Test serialización JSON
        CouponRequest request = new CouponRequest();
        request.setItemIds(Arrays.asList("MLA1", "MLA2"));
        request.setAmount(new BigDecimal("100.50"));
        
        String json = objectMapper.writeValueAsString(request);
        
        assertThat(json).contains("MLA1");
        assertThat(json).contains("MLA2");
        assertThat(json).contains("100.5");
    }

    @Test
    void testCouponRequestDeserialization() throws Exception {
        // Test deserialización JSON
        String json = "{\"item_ids\":[\"MLA1\",\"MLA2\"],\"amount\":250.75}";
        
        CouponRequest request = objectMapper.readValue(json, CouponRequest.class);
        
        assertThat(request.getItemIds()).containsExactly("MLA1", "MLA2");
        assertThat(request.getAmount()).isEqualByComparingTo(new BigDecimal("250.75"));
    }

    @Test
    void testCouponResponseCreation() {
        // Test creación de respuesta
        List<String> selectedItems = Arrays.asList("MLA1", "MLA3");
        BigDecimal total = new BigDecimal("450.25");
        
        CouponResponse response = new CouponResponse();
        response.setItemIds(selectedItems);
        response.setTotal(total);
        
        assertThat(response.getItemIds()).isEqualTo(selectedItems);
        assertThat(response.getTotal()).isEqualTo(total);
    }

    @Test
    void testCouponResponseSerialization() throws Exception {
        // Test serialización de respuesta
        CouponResponse response = new CouponResponse();
        response.setItemIds(Arrays.asList("MLA1", "MLA3"));
        response.setTotal(new BigDecimal("299.99"));
        
        String json = objectMapper.writeValueAsString(response);
        
        assertThat(json).contains("MLA1");
        assertThat(json).contains("MLA3");
        assertThat(json).contains("299.99");
    }

    @Test
    void testMeliItemResponseMapping() throws Exception {
        // Test mapeo desde respuesta de API externa
        String meliApiResponse = "{\"id\":\"MLA123\",\"price\":150.0,\"title\":\"Test Item\"}";
        
        MeliItemResponse item = objectMapper.readValue(meliApiResponse, MeliItemResponse.class);
        
        assertThat(item.getId()).isEqualTo("MLA123");
        assertThat(item.getPrice()).isEqualByComparingTo(new BigDecimal("150.0"));
    }

    @Test
    void testMeliItemResponseWithNullFields() throws Exception {
        // Test manejo de campos nulos
        String incompleteResponse = "{\"id\":\"MLA123\",\"price\":null}";
        
        MeliItemResponse item = objectMapper.readValue(incompleteResponse, MeliItemResponse.class);
        
        assertThat(item.getId()).isEqualTo("MLA123");
        assertThat(item.getPrice()).isNull();
    }

    @Test
    void testEmptyItemIdsList() {
        // Test con lista vacía de items
        CouponRequest request = new CouponRequest();
        request.setItemIds(Arrays.asList());
        request.setAmount(new BigDecimal("100"));
        
        assertThat(request.getItemIds()).isEmpty();
        assertThat(request.getAmount()).isPositive();
    }

    @Test
    void testNegativeAmount() {
        // Test con monto negativo
        CouponRequest request = new CouponRequest();
        request.setItemIds(Arrays.asList("MLA1"));
        request.setAmount(new BigDecimal("-50"));
        
        // Dependiendo de tu implementación, podrías querer validar esto
        assertThat(request.getAmount()).isNegative();
    }

    @Test
    void testZeroAmount() {
        // Test con monto cero
        CouponRequest request = new CouponRequest();
        request.setItemIds(Arrays.asList("MLA1"));
        request.setAmount(BigDecimal.ZERO);
        
        assertThat(request.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void testLargeAmountPrecision() {
        // Test con monto de alta precisión
        BigDecimal preciseAmount = new BigDecimal("999999.99");
        CouponRequest request = new CouponRequest();
        request.setAmount(preciseAmount);
        
        assertThat(request.getAmount()).isEqualByComparingTo(preciseAmount);
    }

    @Test
    void testDuplicateItemIds() {
        // Test con IDs duplicados
        List<String> duplicateIds = Arrays.asList("MLA1", "MLA2", "MLA1");
        CouponRequest request = new CouponRequest();
        request.setItemIds(duplicateIds);
        
        assertThat(request.getItemIds()).hasSize(3);
        assertThat(request.getItemIds()).containsExactly("MLA1", "MLA2", "MLA1");
    }

    @Test
    void testEmptyResponse() {
        // Test respuesta vacía
        CouponResponse response = new CouponResponse();
        response.setItemIds(Arrays.asList());
        response.setTotal(BigDecimal.ZERO);
        
        assertThat(response.getItemIds()).isEmpty();
        assertThat(response.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
