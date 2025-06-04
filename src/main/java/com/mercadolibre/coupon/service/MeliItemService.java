package com.mercadolibre.coupon.service;

import com.mercadolibre.coupon.dto.MeliItemResponse;
import com.mercadolibre.coupon.model.Item;
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
    
    private final WebClient webClient;
    // Cache crítico para 100K RPM - mismo item favorito de muchos usuarios
    private final Map<String, Item> itemCache = new ConcurrentHashMap<>();
    
    public MeliItemService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.mercadolibre.com")
                // Configuración para alto throughput
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
    
    public CompletableFuture<Item> getItemPrice(String itemId) {
        // Cache hit crítico para escalar
        if (itemCache.containsKey(itemId)) {
            return CompletableFuture.completedFuture(itemCache.get(itemId));
        }
        
        return webClient.get()
                .uri("/items/{itemId}", itemId)
                .retrieve()
                .bodyToMono(MeliItemResponse.class)
                .timeout(Duration.ofSeconds(10)) // Timeout más agresivo
                .map(response -> {
                    Item item = new Item(response.getId(), response.getPrice());
                    // Cache con TTL implícito por restart de instancia
                    itemCache.put(itemId, item);
                    return item;
                })
                .onErrorReturn(new Item(itemId, BigDecimal.ZERO)) // Fallar silenciosamente
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
