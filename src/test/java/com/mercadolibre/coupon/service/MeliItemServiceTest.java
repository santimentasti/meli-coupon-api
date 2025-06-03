package com.mercadolibre.coupon.service;

import com.mercadolibre.coupon.dto.MeliItemResponse;
import com.mercadolibre.coupon.model.Item;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests del Servicio de Items de MercadoLibre")
class MeliItemServiceTest {

    private MockWebServer mockWebServer;
    private MeliItemService meliItemService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        String baseUrl = mockWebServer.url("/").toString();
        WebClient.Builder webClientBuilder = WebClient.builder();
        
        // Crear el servicio con la URL del mock server
        meliItemService = new MeliItemService(webClientBuilder) {
            // Override para usar mock server URL
            {
                java.lang.reflect.Field webClientField;
                try {
                    webClientField = MeliItemService.class.getDeclaredField("webClient");
                    webClientField.setAccessible(true);
                    webClientField.set(this, webClientBuilder.baseUrl(baseUrl).build());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("Debe obtener precio de item exitosamente")
    void getItemPrice_Success() throws Exception {
        // Given
        String itemId = "MLA123456789";
        String mockResponse = """
            {
                "id": "MLA123456789",
                "title": "iPhone 13",
                "price": 999.99,
                "site_id": "MLA"
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setBody(mockResponse)
            .addHeader("Content-Type", "application/json"));

        // When
        CompletableFuture<Item> future = meliItemService.getItemPrice(itemId);
        Item result = future.get(5, TimeUnit.SECONDS);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(itemId);
        assertThat(result.getPrice()).isEqualTo(new BigDecimal("999.99"));

        // Verificar la llamada HTTP
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getPath()).isEqualTo("/items/" + itemId);
        assertThat(recordedRequest.getMethod()).isEqualTo("GET");
    }

    @Test
    @DisplayName("Debe cachear items para evitar llamadas duplicadas")
    void getItemPrice_CacheHit() throws Exception {
        // Given
        String itemId = "MLA123456789";
        String mockResponse = """
            {
                "id": "MLA123456789",
                "title": "iPhone 13",
                "price": 999.99
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setBody(mockResponse)
            .addHeader("Content-Type", "application/json"));

        // When - Primera llamada
        CompletableFuture<Item> future1 = meliItemService.getItemPrice(itemId);
        Item result1 = future1.get(5, TimeUnit.SECONDS);

        // When - Segunda llamada (debería usar cache)
        CompletableFuture<Item> future2 = meliItemService.getItemPrice(itemId);
        Item result2 = future2.get(5, TimeUnit.SECONDS);

        // Then
        assertThat(result1).isEqualTo(result2);
        assertThat(result1.getId()).isEqualTo(itemId);
        
        // Solo debería haber una llamada HTTP
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Debe manejar error HTTP y retornar item con precio cero")
    void getItemPrice_HttpError() throws Exception {
        // Given
        String itemId = "MLA404";
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));

        // When
        CompletableFuture<Item> future = meliItemService.getItemPrice(itemId);
        Item result = future.get(5, TimeUnit.SECONDS);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(itemId);
        assertThat(result.getPrice()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Debe manejar timeout y retornar item con precio cero")
    void getItemPrice_Timeout() throws Exception {
        // Given
        String itemId = "MLA_TIMEOUT";
        mockWebServer.enqueue(new MockResponse()
            .setBodyDelay(5, TimeUnit.SECONDS)); // Delay mayor al timeout del servicio

        // When
        CompletableFuture<Item> future = meliItemService.getItemPrice(itemId);
        Item result = future.get(10, TimeUnit.SECONDS);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(itemId);
        assertThat(result.getPrice()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Debe obtener múltiples items en paralelo")
    void getItemsPrices_MultipleItems() throws Exception {
        // Given
        List<String> itemIds = Arrays.asList("MLA1", "MLA2", "MLA3");
        
        // Mock responses para cada item
        mockWebServer.enqueue(new MockResponse()
            .setBody("""
                {
                    "id": "MLA1",
                    "title": "Item 1",
                    "price": 100.00
                }
                """)
            .addHeader("Content-Type", "application/json"));
            
        mockWebServer.enqueue(new MockResponse()
            .setBody("""
                {
                    "id": "MLA2", 
                    "title": "Item 2",
                    "price": 200.00
                }
                """)
            .addHeader("Content-Type", "application/json"));
            
        mockWebServer.enqueue(new MockResponse()
            .setBody("""
                {
                    "id": "MLA3",
                    "title": "Item 3", 
                    "price": 300.00
                }
                """)
            .addHeader("Content-Type", "application/json"));

        // When
        CompletableFuture<List<Item>> future = meliItemService.getItemsPrices(itemIds);
        List<Item> results = future.get(10, TimeUnit.SECONDS);

        // Then
        assertThat(results).hasSize(3);
        assertThat(results)
            .extracting(Item::getId)
            .containsExactlyInAnyOrder("MLA1", "MLA2", "MLA3");
        assertThat(results)
            .extracting(Item::getPrice)
            .containsExactlyInAnyOrder(
                new BigDecimal("100.00"),
                new BigDecimal("200.00"), 
                new BigDecimal("300.00")
            );

        // Verificar que se hicieron 3 llamadas HTTP
        assertThat(mockWebServer.getRequestCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Debe filtrar items con precio cero en llamadas múltiples")
    void getItemsPrices_FilterZeroPriceItems() throws Exception {
        // Given
        List<String> itemIds = Arrays.asList("MLA1", "MLA404", "MLA3");
        
        // Respuesta exitosa para MLA1
        mockWebServer.enqueue(new MockResponse()
            .setBody("""
                {
                    "id": "MLA1",
                    "price": 100.00
                }
                """)
            .addHeader("Content-Type", "application/json"));
            
        // Error 404 para MLA404
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));
        
        // Respuesta exitosa para MLA3
        mockWebServer.enqueue(new MockResponse()
            .setBody("""
                {
                    "id": "MLA3",
                    "price": 300.00
                }
                """)
            .addHeader("Content-Type", "application/json"));

        // When
        CompletableFuture<List<Item>> future = meliItemService.getItemsPrices(itemIds);
        List<Item> results = future.get(10, TimeUnit.SECONDS);

        // Then - Solo debería retornar los items con precio > 0
        assertThat(results).hasSize(2);
        assertThat(results)
            .extracting(Item::getId)
            .containsExactlyInAnyOrder("MLA1", "MLA3");
        assertThat(results)
            .allMatch(item -> item.getPrice().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Debe manejar lista vacía de items")
    void getItemsPrices_EmptyList() throws Exception {
        // Given
        List<String> emptyList = Arrays.asList();

        // When
        CompletableFuture<List<Item>> future = meliItemService.getItemsPrices(emptyList);
        List<Item> results = future.get(5, TimeUnit.SECONDS);

        // Then
        assertThat(results).isEmpty();
        assertThat(mockWebServer.getRequestCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Debe manejar respuesta JSON inválida")
    void getItemPrice_InvalidJson() throws Exception {
        // Given
        String itemId = "MLA_INVALID";
        mockWebServer.enqueue(new MockResponse()
            .setBody("invalid json response")
            .addHeader("Content-Type", "application/json"));

        // When
        CompletableFuture<Item> future = meliItemService.getItemPrice(itemId);
        Item result = future.get(5, TimeUnit.SECONDS);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(itemId);
        assertThat(result.getPrice()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Debe procesar items con precios decimales correctamente")
    void getItemPrice_DecimalPrices() throws Exception {
        // Given
        String itemId = "MLA_DECIMAL";
        String mockResponse = """
            {
                "id": "MLA_DECIMAL",
                "title": "Item with decimal price",
                "price": 123.45
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setBody(mockResponse)
            .addHeader("Content-Type", "application/json"));

        // When
        CompletableFuture<Item> future = meliItemService.getItemPrice(itemId);
        Item result = future.get(5, TimeUnit.SECONDS);

        // Then
        assertThat(result.getPrice()).isEqualTo(new BigDecimal("123.45"));
    }

    @Test
    @DisplayName("Debe manejar respuesta con campos faltantes")
    void getItemPrice_MissingFields() throws Exception {
        // Given
        String itemId = "MLA_PARTIAL";
        String mockResponse = """
            {
                "id": "MLA_PARTIAL"
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setBody(mockResponse)
            .addHeader("Content-Type", "application/json"));

        // When
        CompletableFuture<Item> future = meliItemService.getItemPrice(itemId);
        Item result = future.get(5, TimeUnit.SECONDS);

        // Then - Debería manejar gracefully el precio null
        assertThat(result.getId()).isEqualTo(itemId);
        // El precio podría ser null o cero dependiendo del mapeo
    }

    @Test
    @DisplayName("Debe manejar alta concurrencia sin problemas")
    void getItemsPrices_HighConcurrency() throws Exception {
        // Given - Lista grande de items para simular alta carga
        List<String> manyItemIds = Arrays.asList(
            "MLA1", "MLA2", "MLA3", "MLA4", "MLA5", 
            "MLA6", "MLA7", "MLA8", "MLA9", "MLA10"
        );
        
        // Enqueue respuestas para todos los items
        for (int i = 1; i <= 10; i++) {
            mockWebServer.enqueue(new MockResponse()
                .setBody(String.format("""
                    {
                        "id": "MLA%d",
                        "price": %d00.00
                    }
                    """, i, i))
                .addHeader("Content-Type", "application/json"));
        }

        // When
        long startTime = System.currentTimeMillis();
        CompletableFuture<List<Item>> future = meliItemService.getItemsPrices(manyItemIds);
        List<Item> results = future.get(15, TimeUnit.SECONDS);
        long executionTime = System.currentTimeMillis() - startTime;

        // Then
        assertThat(results).hasSize(10);
        assertThat(executionTime).isLessThan(5000); // Debería completarse rápido por paralelización
        assertThat(mockWebServer.getRequestCount()).isEqualTo(10);
        
        // Verificar que todos los precios son correctos
        assertThat(results)
            .allMatch(item -> item.getPrice().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Debe retornar lista vacía en caso de timeout global")
    void getItemsPrices_GlobalTimeout() throws Exception {
        // Given
        List<String> itemIds = Arrays.asList("MLA1", "MLA2");
        
        // Simular respuestas muy lentas
        mockWebServer.enqueue(new MockResponse()
            .setBodyDelay(20, TimeUnit.SECONDS));
        mockWebServer.enqueue(new MockResponse()
            .setBodyDelay(20, TimeUnit.SECONDS));

        // When
        CompletableFuture<List<Item>> future = meliItemService.getItemsPrices(itemIds);
        List<Item> results = future.get(20, TimeUnit.SECONDS);

        // Then - Debería retornar lista vacía por timeout
        assertThat(results).isEmpty();
    }
}