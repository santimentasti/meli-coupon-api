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
        // Configuración inicial: servidor HTTP simulado para interceptar llamadas reales a la API
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        String baseUrl = mockWebServer.url("/").toString();
        WebClient.Builder webClientBuilder = WebClient.builder();
        
        // Crea el servicio usando reflexión para inyectar la URL del servidor simulado
        meliItemService = new MeliItemService(webClientBuilder) {
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
    public void tearDown() throws IOException {
        if (mockWebServer != null) {
            try {
                // Cancel any pending requests first
                mockWebServer.shutdown();
            } catch (IOException e) {
                // Force shutdown if normal shutdown fails
                System.err.println("MockWebServer shutdown timeout, forcing close");
            }
        }
    }

    @Test
    @DisplayName("Debe obtener precio de item exitosamente con estructura real de API")
    void getItemPrice_Success_RealApiStructure() throws Exception {
        // ARRANGE: Usar la estructura real de respuesta de MercadoLibre API
        String itemId = "MLA599260060";
        String realApiResponse = """
            {
                "id": "MLA599260060",
                "site_id": "MLA",
                "title": "Item De Test - Por Favor No Ofertar",
                "subtitle": null,
                "seller_id": 303888594,
                "category_id": "MLA401685",
                "official_store_id": null,
                "price": 130,
                "base_price": 130,
                "original_price": null,
                "currency_id": "ARS",
                "initial_quantity": 1,
                "available_quantity": 1,
                "sale_terms": [],
                "automatic_relist": false,
                "date_created": "2018-02-26T18:15:05.000Z",
                "last_updated": "2018-03-29T04:14:39.000Z",
                "health": null
            }
            """;
        
        // Configura el servidor simulado con la respuesta real de la API
        mockWebServer.enqueue(new MockResponse()
            .setBody(realApiResponse)
            .addHeader("Content-Type", "application/json"));

        // ACT: Ejecutar el método bajo prueba
        CompletableFuture<Item> future = meliItemService.getItemPrice(itemId);
        Item result = future.get(5, TimeUnit.SECONDS);

        // ASSERT: Verificar que el mapeo de la estructura real funciona correctamente
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(itemId);
        assertThat(result.getPrice()).isEqualTo(new BigDecimal("130")); // Precio como entero en la API real

        // Verificar la llamada HTTP realizada
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getPath()).isEqualTo("/items/" + itemId);
        assertThat(recordedRequest.getMethod()).isEqualTo("GET");
    }

    @Test
    @DisplayName("Debe manejar múltiples items con estructura real de API en paralelo")
    void getItemsPrices_MultipleItems_RealStructure() throws Exception {
        // ARRANGE: Múltiples items con estructura real de MercadoLibre
        List<String> itemIds = Arrays.asList("MLA1", "MLA2", "MLA3");
        
        // Respuesta para primer item - precio entero
        mockWebServer.enqueue(new MockResponse()
            .setBody("""
                {
                    "id": "MLA1",
                    "site_id": "MLA",
                    "title": "Producto 1",
                    "seller_id": 123456,
                    "category_id": "MLA401685",
                    "price": 100,
                    "base_price": 100,
                    "currency_id": "ARS",
                    "initial_quantity": 5,
                    "available_quantity": 5,
                    "date_created": "2023-01-01T10:00:00.000Z"
                }
                """)
            .addHeader("Content-Type", "application/json"));
            
        // Respuesta para segundo item - precio con decimales
        mockWebServer.enqueue(new MockResponse()
            .setBody("""
                {
                    "id": "MLA2",
                    "site_id": "MLA", 
                    "title": "Producto 2",
                    "seller_id": 654321,
                    "category_id": "MLA401685",
                    "price": 250.75,
                    "base_price": 250.75,
                    "currency_id": "ARS",
                    "initial_quantity": 10,
                    "available_quantity": 8,
                    "date_created": "2023-01-02T15:30:00.000Z"
                }
                """)
            .addHeader("Content-Type", "application/json"));
            
        // Respuesta para tercer item - precio alto
        mockWebServer.enqueue(new MockResponse()
            .setBody("""
                {
                    "id": "MLA3",
                    "site_id": "MLA",
                    "title": "Producto Premium",
                    "seller_id": 789012,
                    "category_id": "MLA401685",
                    "price": 15000,
                    "base_price": 18000,
                    "original_price": 20000,
                    "currency_id": "ARS",
                    "initial_quantity": 1,
                    "available_quantity": 1,
                    "date_created": "2023-01-03T09:15:00.000Z"
                }
                """)
            .addHeader("Content-Type", "application/json"));

        // ACT: Solicitar múltiples items con estructura real
        CompletableFuture<List<Item>> future = meliItemService.getItemsPrices(itemIds);
        List<Item> results = future.get(10, TimeUnit.SECONDS);

        // ASSERT: Verificar procesamiento correcto de estructura real
        assertThat(results).hasSize(3);
        assertThat(results)
            .extracting(Item::getId)
            .containsExactlyInAnyOrder("MLA1", "MLA2", "MLA3");
        
        // Verificar que los precios se mapean correctamente desde la estructura real
        assertThat(results)
            .extracting(Item::getPrice)
            .containsExactlyInAnyOrder(
                new BigDecimal("100"),      // Precio entero
                new BigDecimal("250.75"),   // Precio con decimales
                new BigDecimal("15000")     // Precio alto
            );

        assertThat(mockWebServer.getRequestCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Debe manejar item sin stock disponible (available_quantity = 0)")
    void getItemPrice_NoStock() throws Exception {
        // ARRANGE: Item sin stock disponible en la estructura real
        String itemId = "MLA_NO_STOCK";
        String noStockResponse = """
            {
                "id": "MLA_NO_STOCK",
                "site_id": "MLA",
                "title": "Producto Agotado",
                "seller_id": 123456,
                "category_id": "MLA401685",
                "price": 500,
                "base_price": 500,
                "currency_id": "ARS",
                "initial_quantity": 10,
                "available_quantity": 0,
                "status": "paused",
                "date_created": "2023-01-01T10:00:00.000Z"
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setBody(noStockResponse)
            .addHeader("Content-Type", "application/json"));

        // ACT: Obtener item sin stock
        CompletableFuture<Item> future = meliItemService.getItemPrice(itemId);
        Item result = future.get(5, TimeUnit.SECONDS);

        // ASSERT: Verificar comportamiento con item sin stock
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(itemId);
        // El servicio podría retornar precio 0 para items sin stock o el precio real
        // Dependiendo de la lógica de negocio implementada
        assertThat(result.getPrice()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Debe manejar item pausado o inactivo")
    void getItemPrice_InactiveItem() throws Exception {
        // ARRANGE: Item con status inactivo
        String itemId = "MLA_INACTIVE";
        String inactiveItemResponse = """
            {
                "id": "MLA_INACTIVE",
                "site_id": "MLA",
                "title": "Item Pausado",
                "seller_id": 123456,
                "category_id": "MLA401685",
                "price": 200,
                "base_price": 200,
                "currency_id": "ARS",
                "initial_quantity": 5,
                "available_quantity": 3,
                "status": "paused",
                "date_created": "2023-01-01T10:00:00.000Z"
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setBody(inactiveItemResponse)
            .addHeader("Content-Type", "application/json"));

        // ACT: Obtener item pausado
        CompletableFuture<Item> future = meliItemService.getItemPrice(itemId);
        Item result = future.get(5, TimeUnit.SECONDS);

        // ASSERT: El servicio debe manejar items pausados apropiadamente
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(itemId);
        // Dependiendo de la lógica de negocio, podría retornar precio 0 o el precio real
    }

    @Test
    @DisplayName("Debe manejar timeout con estructura de respuesta real")
    void getItemPrice_Timeout_RealStructure() throws Exception {
        // ARRANGE: Configurar timeout con estructura real (pero nunca se completará)
        String itemId = "MLA_TIMEOUT";
        String timeoutResponse = """
            {
                "id": "MLA_TIMEOUT",
                "site_id": "MLA",
                "title": "Item Lento",
                "price": 999,
                "currency_id": "ARS"
            }
            """;
        
        // Respuesta que nunca llegará a tiempo debido al delay
        mockWebServer.enqueue(new MockResponse()
            .setBody(timeoutResponse)
            .setBodyDelay(15, TimeUnit.SECONDS));

        // ACT: Intentar obtener item con timeout
        CompletableFuture<Item> future = meliItemService.getItemPrice(itemId);
        Item result = future.get(20, TimeUnit.SECONDS);

        // ASSERT: Manejar timeout apropiadamente
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(itemId);
        assertThat(result.getPrice()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Debe manejar campos opcionales faltantes en estructura real")
    void getItemPrice_MissingOptionalFields() throws Exception {
        // ARRANGE: Respuesta con solo campos obligatorios (algunos opcionales faltantes)
        String itemId = "MLA_MINIMAL";
        String minimalResponse = """
            {
                "id": "MLA_MINIMAL",
                "site_id": "MLA",
                "title": "Item Mínimo",
                "price": 50,
                "currency_id": "ARS"
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setBody(minimalResponse)
            .addHeader("Content-Type", "application/json"));

        // ACT: Procesar respuesta con campos mínimos
        CompletableFuture<Item> future = meliItemService.getItemPrice(itemId);
        Item result = future.get(5, TimeUnit.SECONDS);

        // ASSERT: El mapeo debe funcionar con campos opcionales faltantes
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(itemId);
        assertThat(result.getPrice()).isEqualTo(new BigDecimal("50"));
    }

    @Test
    @DisplayName("Debe manejar precios en diferentes monedas")
    void getItemPrice_DifferentCurrencies() throws Exception {
        // ARRANGE: Items con diferentes monedas (USD, ARS)
        List<String> itemIds = Arrays.asList("MLA_ARS", "MLA_USD");
        
        // Item en pesos argentinos
        mockWebServer.enqueue(new MockResponse()
            .setBody("""
                {
                    "id": "MLA_ARS",
                    "site_id": "MLA",
                    "title": "Producto en Pesos",
                    "price": 10000,
                    "currency_id": "ARS"
                }
                """)
            .addHeader("Content-Type", "application/json"));
            
        // Item en dólares (si está disponible en MLA)
        mockWebServer.enqueue(new MockResponse()
            .setBody("""
                {
                    "id": "MLA_USD",
                    "site_id": "MLA",
                    "title": "Producto en Dólares",
                    "price": 100,
                    "currency_id": "USD"
                }
                """)
            .addHeader("Content-Type", "application/json"));

        // ACT: Procesar items con diferentes monedas
        CompletableFuture<List<Item>> future = meliItemService.getItemsPrices(itemIds);
        List<Item> results = future.get(10, TimeUnit.SECONDS);

        // ASSERT: Verificar que se manejan diferentes monedas
        assertThat(results).hasSize(2);
        assertThat(results)
            .extracting(Item::getPrice)
            .containsExactlyInAnyOrder(
                new BigDecimal("10000"),  // ARS
                new BigDecimal("100")     // USD
            );
        
        // Nota: La lógica de conversión de monedas dependerá de los requerimientos del negocio
    }

    @Test
    @DisplayName("Debe filtrar items con precio cero usando estructura real")
    void getItemsPrices_FilterZeroPriceItems_RealStructure() throws Exception {
        // ARRANGE: Mix de items válidos, con error 404, y con precio 0
        List<String> itemIds = Arrays.asList("MLA_VALID", "MLA_404", "MLA_ZERO_PRICE");
        
        // Item válido con precio normal
        mockWebServer.enqueue(new MockResponse()
            .setBody("""
                {
                    "id": "MLA_VALID",
                    "site_id": "MLA",
                    "title": "Producto Válido",
                    "price": 500,
                    "currency_id": "ARS",
                    "available_quantity": 10
                }
                """)
            .addHeader("Content-Type", "application/json"));
            
        // Error 404 para item inexistente
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));
        
        // Item con precio 0 (podría ser promocional o error)
        mockWebServer.enqueue(new MockResponse()
            .setBody("""
                {
                    "id": "MLA_ZERO_PRICE",
                    "site_id": "MLA",
                    "title": "Producto Gratis",
                    "price": 0,
                    "currency_id": "ARS",
                    "available_quantity": 1
                }
                """)
            .addHeader("Content-Type", "application/json"));

        // ACT: Procesar items mixtos
        CompletableFuture<List<Item>> future = meliItemService.getItemsPrices(itemIds);
        List<Item> results = future.get(10, TimeUnit.SECONDS);

        // ASSERT: Solo debe retornar items con precio > 0
        assertThat(results).hasSize(1); // Solo el item válido
        assertThat(results.get(0).getId()).isEqualTo("MLA_VALID");
        assertThat(results.get(0).getPrice()).isEqualTo(new BigDecimal("500"));
        
        // Verificar que se filtraron correctamente los items con precio 0 o error
        assertThat(results)
            .allMatch(item -> item.getPrice().compareTo(BigDecimal.ZERO) > 0);
    }
}