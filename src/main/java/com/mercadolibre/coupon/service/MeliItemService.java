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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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
    
    public CompletableFuture<List<Item>> getItemsPrices(List<String> itemIds) {
        // Paralelización crítica para miles de items favoritos
        return Flux.fromIterable(itemIds)
                .flatMap(itemId -> 
                    Mono.fromFuture(getItemPrice(itemId))
                        .onErrorReturn(new Item(itemId, BigDecimal.ZERO)),
                    50) // Concurrency de 50 requests paralelos
                .filter(item -> item.getPrice().compareTo(BigDecimal.ZERO) > 0)
                .collectList()
                .timeout(Duration.ofSeconds(15)) // Timeout razonable para miles de items
                .onErrorReturn(List.of()) // Fallar gracefully
                .toFuture();
    }
}
