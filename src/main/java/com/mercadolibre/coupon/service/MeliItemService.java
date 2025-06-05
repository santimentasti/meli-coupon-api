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
    
    public MeliItemService(WebClient.Builder webClientBuilder, @Value("${meli.access-token}") String accessToken) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.mercadolibre.com")
                .defaultHeader("Authorization", "Bearer " + accessToken) // token desde env
             // Configuración para alto throughput
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
    
 // Método auxiliar para obtener el Access Token
    private String getAccessToken() {
        // ¡ADVERTENCIA! Esto es para DEMOSTRACIÓN.
        // En un entorno de producción, el token se obtendría del contexto de seguridad
        // del usuario o de una base de datos segura, NO de una variable estática de un controlador.
        AccessTokenResponse token = AuthController.getCurrentAccessTokenForService();
        if (token != null) {
            return token.getAccessToken();
        }
        // Manejar el caso donde no hay token, quizás lanzando una excepción o retornando null
        System.err.println("Advertencia: No se encontró un Access Token. Las solicitudes a Meli pueden fallar.");
        return null; // O lanzar una RuntimeException
    }
    
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
                .header("Authorization", "Bearer " + currentAccessToken) // Añadir el header de autorización
                .retrieve()
                .bodyToMono(MeliItemResponse.class)
                .timeout(Duration.ofSeconds(10))
                .map(response -> {
                    Item item = new Item(response.getId(), response.getPrice());
                    itemCache.put(itemId, item);
                    return item;
                })
                .onErrorResume(e -> { // Usar onErrorResume para manejar errores de WebClient
                    System.err.println("Error fetching item " + itemId + ": " + e.getMessage());
                    return Mono.just(new Item(itemId, BigDecimal.ZERO)); // Fallar silenciosamente con un item de precio cero
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
