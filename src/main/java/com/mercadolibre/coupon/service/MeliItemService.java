package com.mercadolibre.coupon.service;

import com.mercadolibre.coupon.controller.AuthController;
import com.mercadolibre.coupon.dto.AccessTokenResponse;
import com.mercadolibre.coupon.dto.MeliItemResponse;
import com.mercadolibre.coupon.model.Item;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class MeliItemService {
	
	@Value("${meli.access-token}")
    private String accessToken;
    
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
                // NO ponemos Authorization header aquí - se agrega dinámicamente
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
    public CompletableFuture<List<Item>> getItemsPrices(List<String> itemIds) { // Ya no necesita 'accessToken' como parámetro
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

        // 5. Realizar la llamada batch a la API de Mercado Libre
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/items") // El path base es /items
                    .queryParam("ids", itemIdsString) // El query param es 'ids'
                    .build())
                .header("Authorization", "Bearer " + currentAccessToken) // Añadir el header de autorización
                .retrieve()
                .bodyToFlux(MeliItemResponse.class) // Esperamos un Flux (lista) de MeliItemResponse
                .timeout(Duration.ofSeconds(15)) // Un timeout un poco más generoso para llamadas batch
                .collectList() // Recopilar todos los elementos en una lista
                .map(meliResponses -> {
                    List<Item> fetchedItems = meliResponses.stream()
                        // Filtramos las respuestas que no tienen ID o precio válido
                        .filter(response -> response != null && response.getId() != null && response.getPrice() != null && response.getPrice().compareTo(BigDecimal.ZERO) > 0)
                        .map(response -> new Item(response.getId(), response.getPrice()))
                        .collect(Collectors.toList());

                    // Actualizar el caché con los ítems recién obtenidos
                    fetchedItems.forEach(item -> itemCache.put(item.getId(), item));

                    // 6. Combinar los ítems de la caché con los ítems recién obtenidos
                    // Aseguramos que la lista final tenga los IDs originales y sus precios (0 si no se encontraron)
                    return itemIds.stream().map(id -> {
                                      Item item = itemCache.get(id);
                                      // Si un item solicitado no se encontró en la API (y por ende no en caché), lo consideramos como precio 0
                                      if (item == null) {
                                          System.err.println("Advertencia: Item " + id + " no encontrado en la respuesta de Mercado Libre o tenía precio inválido.");
                                          return new Item(id, BigDecimal.ZERO);
                                      }
                                      return item;
                                  })
                                  .collect(Collectors.toList());
                }).onErrorResume(e -> { // Manejo de errores para la llamada batch completa
                    System.err.println("Error fetching batch items: " + e.getMessage());
                    // En caso de error general de la llamada batch, devolvemos una lista de items con precio cero
                    return Mono.just(itemIds.stream()
                                            .map(id -> new Item(id, BigDecimal.ZERO))
                                            .collect(Collectors.toList()));
                })
                .toFuture();
    }
}
