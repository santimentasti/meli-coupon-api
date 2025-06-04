package com.mercadolibre.coupon.controller;

import com.mercadolibre.coupon.dto.CouponRequest;
import com.mercadolibre.coupon.dto.CouponResponse;
import com.mercadolibre.coupon.model.Item;
import com.mercadolibre.coupon.service.CouponOptimizationService;
import com.mercadolibre.coupon.service.MeliItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/coupon")
@CrossOrigin(origins = "*") // Para testing desde cualquier origen
public class CouponController {
    
    @Autowired
    private MeliItemService meliItemService;
    
    @Autowired
    private CouponOptimizationService optimizationService;
    
    @PostMapping
    public CompletableFuture<ResponseEntity<CouponResponse>> calculateOptimalItems(
            @Valid @RequestBody CouponRequest request) {
        	return meliItemService.getItemsPrices(request.getItemIds())
                .thenApply(items -> {
                    List<String> optimalItemIds = optimizationService
                            .findOptimalItems(items, request.getAmount());
                    System.out.println("Items recuperados: " + items);
                    BigDecimal total = calculateTotal(items, optimalItemIds);
                    
                    CouponResponse response = new CouponResponse(optimalItemIds, total);
                    return ResponseEntity.ok(response);
                })
                .exceptionally(throwable -> {
                    // Log error produccion
                    System.err.println("Error coupon request: " + throwable.getMessage());
                    return ResponseEntity.internalServerError()
                            .body(new CouponResponse(List.of(), BigDecimal.ZERO));
                });
    }
    
    private BigDecimal calculateTotal(List<Item> items, List<String> selectedIds) {
        return items.stream()
                .filter(item -> selectedIds.contains(item.getId()))
                .map(Item::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    // Endpoint adicional para health check
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("API corriendo");
    }
}
