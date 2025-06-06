package com.mercadolibre.coupon.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mercadolibre.coupon.controller.AuthController;
import com.mercadolibre.coupon.dto.AccessTokenResponse;
import com.mercadolibre.coupon.dto.MeliItemResponse;
import com.mercadolibre.coupon.model.Item;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class MeliItemService {
    
    private final WebClient webClient;
    // Cache crítico para 100K RPM - mismo item favorito de muchos usuarios
    private final Map<String, Item> itemCache = new ConcurrentHashMap<>();
    
    private String getAccessToken() {
        AccessTokenResponse token = AuthController.getCurrentAccessTokenForService();
        if (token != null) {
            return token.getAccessToken();
        }
        
        System.err.println("Advertencia: No se encontró un Access Token. Las solicitudes a Meli fallarán.");
        return null;
    }
    
    public MeliItemService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.mercadolibre.com")
                // NO se agrega Authorization header aquí - se agrega dinámicamente
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
    
 // Método auxiliar para obtener el Access Token
    
    
    public CompletableFuture<Item> getItemPrice(String itemId) {
        // Cache hit crítico para escalar
        if (itemCache.containsKey(itemId)) {
            return CompletableFuture.completedFuture(itemCache.get(itemId));
        }
        
        String currentAccessToken = getAccessToken(); // Obtener el token dinámicamente
        if (currentAccessToken == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Access Token no disponible."));
        }
        
        return webClient.get()
                .uri("/items/{itemId}", itemId)
                .header("Authorization", "Bearer " + currentAccessToken)
                .retrieve()
                .bodyToMono(MeliItemResponse.class)
                .timeout(Duration.ofSeconds(10))
                .map(response -> {
                    Item item = new Item(response.getId(), response.getPrice());
                    itemCache.put(itemId, item);
                    return item;
                })
                .onErrorResume(e -> {
                    System.err.println("Error fetching item " + itemId + ": " + e.getMessage());
                    // En caso de error 401, el token podría estar expirado
                    if (e.getMessage().contains("401")) {
                        System.err.println("Posible token expirado para item: " + itemId);
                    }
                    return Mono.just(new Item(itemId, BigDecimal.ZERO));
                })
                .toFuture();
    }
    
    /**
     * Obtiene los precios de una lista de items de Mercado Libre haciendo una única llamada batch.
     * Utiliza caché para items ya consultados.
     * Si un item no se encuentra o hay un error, se devuelve con precio 0.
     *
     * @param itemIds Lista de IDs de ítems a consultar.
     * @return Un CompletableFuture que contendrá la lista de ítems con sus precios.
     */
    public CompletableFuture<List<Item>> getItemsPrices(List<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        // 1. Identificar qué ítems ya están en caché y cuáles necesitan ser consultados a la API
        Set<String> idsToFetch = itemIds.stream()
                                    .filter(id -> !itemCache.containsKey(id))
                                    .collect(Collectors.toSet());

        // 2. Obtener el token de acceso
        String currentAccessToken = getAccessToken();
        if (currentAccessToken == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Access Token no disponible."));
        }

        // 3. Si todos los ítems ya están en caché, devolvemos directamente desde la caché
        if (idsToFetch.isEmpty()) {
            return CompletableFuture.completedFuture(
                itemIds.stream()
                       .map(itemCache::get)
                       .collect(Collectors.toList())
            );
        }

        // 4. Construir la cadena de IDs para la llamada batch
        String itemIdsString = String.join(",", idsToFetch);

        System.out.println("Consultando items a MercadoLibre: " + itemIdsString);

        // 5. Realizar la llamada batch a la API de Mercado Libre
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/items")
                    .queryParam("ids", itemIdsString)
                    .build())
                .header("Authorization", "Bearer " + currentAccessToken)
                .retrieve()
                .bodyToMono(JsonNode.class) // Recibimos como JsonNode para manejar la estructura compleja
                .timeout(Duration.ofSeconds(15))
                .map(jsonResponse -> {
                    List<Item> fetchedItems = new ArrayList<>();
                    
                    // La API de MercadoLibre devuelve un array de objetos
                    if (jsonResponse.isArray()) {
                        for (JsonNode itemNode : jsonResponse) {
                            try {
                                // Cada elemento tiene una estructura: {"code": 200, "body": {...}}
                                if (itemNode.has("code") && itemNode.get("code").asInt() == 200 && itemNode.has("body")) {
                                    JsonNode bodyNode = itemNode.get("body");
                                    String id = bodyNode.get("id").asText();
                                    BigDecimal price = new BigDecimal(bodyNode.get("price").asText());
                                    
                                    if (price.compareTo(BigDecimal.ZERO) > 0) {
                                        Item item = new Item(id, price);
                                        fetchedItems.add(item);
                                        itemCache.put(id, item);
                                        System.out.println("Item obtenido: " + id + " - Precio: " + price);
                                    }
                                } else {
                                    // Item no encontrado o error
                                    System.err.println("Item no encontrado o con error en respuesta batch");
                                }
                            } catch (Exception e) {
                                System.err.println("Error procesando item en batch: " + e.getMessage());
                            }
                        }
                    }

                    // 6. Combinar resultados: items del cache + items recién obtenidos
                    return itemIds.stream().map(id -> {
                        Item item = itemCache.get(id);
                        if (item == null) {
                            System.err.println("Advertencia: Item " + id + " no encontrado, usando precio 0");
                            return new Item(id, BigDecimal.ZERO);
                        }
                        return item;
                    }).collect(Collectors.toList());
                    
                }).onErrorResume(e -> {
                    System.err.println("Error en llamada batch a MercadoLibre: " + e.getMessage());
                    e.printStackTrace();
                    
                    // En caso de error, devolver items con precio 0
                    return Mono.just(itemIds.stream()
                                            .map(id -> new Item(id, BigDecimal.ZERO))
                                            .collect(Collectors.toList()));
                })
                .toFuture();
    }
    
    /**
     * Método para verificar si hay un token válido disponible
     */
    public boolean hasValidToken() {
        String token = getAccessToken();
        return token != null && !token.isEmpty();
    }
}
