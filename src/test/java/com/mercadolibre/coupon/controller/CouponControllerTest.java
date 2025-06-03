package com.mercadolibre.coupon.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadolibre.coupon.dto.CouponRequest;
import com.mercadolibre.coupon.dto.CouponResponse;
import com.mercadolibre.coupon.model.Item;
import com.mercadolibre.coupon.service.CouponOptimizationService;
import com.mercadolibre.coupon.service.MeliItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CouponController.class)
@DisplayName("Tests del Controlador de Cupones")
class CouponControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MeliItemService meliItemService;

    @MockBean
    private CouponOptimizationService optimizationService;

    @Autowired
    private ObjectMapper objectMapper;

    private CouponRequest validRequest;
    private List<Item> mockItems;

    @BeforeEach
    void setUp() {
        validRequest = new CouponRequest(
            Arrays.asList("MLA1", "MLA2", "MLA3"),
            new BigDecimal("500.00")
        );

        mockItems = Arrays.asList(
            new Item("MLA1", new BigDecimal("100.00")),
            new Item("MLA2", new BigDecimal("200.00")),
            new Item("MLA3", new BigDecimal("150.00"))
        );
    }

    @Test
    @DisplayName("POST /coupon - Debe calcular items óptimos exitosamente")
    void calculateOptimalItems_Success() throws Exception {
        // Given
        List<String> optimalItems = Arrays.asList("MLA1", "MLA3");
        when(meliItemService.getItemsPrices(anyList()))
            .thenReturn(CompletableFuture.completedFuture(mockItems));
        when(optimizationService.findOptimalItems(anyList(), any(BigDecimal.class)))
            .thenReturn(optimalItems);

        // When & Then
        mockMvc.perform(post("/coupon")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item_ids", hasSize(2)))
                .andExpect(jsonPath("$.item_ids", containsInAnyOrder("MLA1", "MLA3")))
                .andExpect(jsonPath("$.total", is(250.00)));

        verify(meliItemService).getItemsPrices(validRequest.getItemIds());
        verify(optimizationService).findOptimalItems(mockItems, validRequest.getAmount());
    }

    @Test
    @DisplayName("POST /coupon - Debe manejar lista vacía de items")
    void calculateOptimalItems_EmptyItemsList() throws Exception {
        // Given
        when(meliItemService.getItemsPrices(anyList()))
            .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(optimizationService.findOptimalItems(anyList(), any(BigDecimal.class)))
            .thenReturn(List.of());

        // When & Then
        mockMvc.perform(post("/coupon")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item_ids", hasSize(0)))
                .andExpect(jsonPath("$.total", is(0)));
    }

    @Test
    @DisplayName("POST /coupon - Debe manejar errores del servicio")
    void calculateOptimalItems_ServiceError() throws Exception {
        // Given
        CompletableFuture<List<Item>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Error al obtener items"));
        when(meliItemService.getItemsPrices(anyList())).thenReturn(failedFuture);

        // When & Then
        mockMvc.perform(post("/coupon")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.item_ids", hasSize(0)))
                .andExpect(jsonPath("$.total", is(0)));
    }

    @Test
    @DisplayName("POST /coupon - Debe validar request inválido - lista vacía")
    void calculateOptimalItems_InvalidRequest_EmptyList() throws Exception {
        // Given
        CouponRequest invalidRequest = new CouponRequest(List.of(), new BigDecimal("500.00"));

        // When & Then
        mockMvc.perform(post("/coupon")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /coupon - Debe validar request inválido - monto nulo")
    void calculateOptimalItems_InvalidRequest_NullAmount() throws Exception {
        // Given
        CouponRequest invalidRequest = new CouponRequest(Arrays.asList("MLA1"), null);

        // When & Then
        mockMvc.perform(post("/coupon")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /coupon - Debe validar request inválido - monto negativo")
    void calculateOptimalItems_InvalidRequest_NegativeAmount() throws Exception {
        // Given
        CouponRequest invalidRequest = new CouponRequest(
            Arrays.asList("MLA1"), 
            new BigDecimal("-100.00")
        );

        // When & Then
        mockMvc.perform(post("/coupon")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /coupon - Debe validar JSON malformado")
    void calculateOptimalItems_MalformedJson() throws Exception {
        // When & Then
        mockMvc.perform(post("/coupon")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /coupon/health - Debe retornar estado OK")
    void healthCheck_Success() throws Exception {
        // When & Then
        mockMvc.perform(get("/coupon/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("API corriendo"));
    }

    @Test
    @DisplayName("POST /coupon - Debe manejar timeout del servicio")
    void calculateOptimalItems_ServiceTimeout() throws Exception {
        // Given
        CompletableFuture<List<Item>> timeoutFuture = new CompletableFuture<>();
        timeoutFuture.completeExceptionally(new CompletionException("Timeout", new RuntimeException()));
        when(meliItemService.getItemsPrices(anyList())).thenReturn(timeoutFuture);

        // When & Then
        mockMvc.perform(post("/coupon")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.item_ids", hasSize(0)))
                .andExpect(jsonPath("$.total", is(0)));
    }

    @Test
    @DisplayName("POST /coupon - Debe calcular total correctamente con items parciales")
    void calculateOptimalItems_PartialItemsSelected() throws Exception {
        // Given
        List<String> optimalItems = Arrays.asList("MLA2"); // Solo uno de los tres items
        when(meliItemService.getItemsPrices(anyList()))
            .thenReturn(CompletableFuture.completedFuture(mockItems));
        when(optimizationService.findOptimalItems(anyList(), any(BigDecimal.class)))
            .thenReturn(optimalItems);

        // When & Then
        mockMvc.perform(post("/coupon")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item_ids", hasSize(1)))
                .andExpect(jsonPath("$.item_ids[0]", is("MLA2")))
                .andExpect(jsonPath("$.total", is(200.00)));
    }

    @Test
    @DisplayName("POST /coupon - Debe manejar items con precios decimales")
    void calculateOptimalItems_DecimalPrices() throws Exception {
        // Given
        List<Item> decimalItems = Arrays.asList(
            new Item("MLA1", new BigDecimal("99.99")),
            new Item("MLA2", new BigDecimal("149.50"))
        );
        List<String> optimalItems = Arrays.asList("MLA1", "MLA2");
        
        when(meliItemService.getItemsPrices(anyList()))
            .thenReturn(CompletableFuture.completedFuture(decimalItems));
        when(optimizationService.findOptimalItems(anyList(), any(BigDecimal.class)))
            .thenReturn(optimalItems);

        // When & Then
        mockMvc.perform(post("/coupon")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(249.49)));
    }
}
