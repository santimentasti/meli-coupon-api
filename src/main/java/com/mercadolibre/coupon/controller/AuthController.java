package com.mercadolibre.coupon.controller;

import com.mercadolibre.coupon.dto.AccessTokenRequest;
import com.mercadolibre.coupon.dto.AccessTokenResponse;
import com.mercadolibre.coupon.service.MeliAuthService; // ¡Importación corregida!
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/mercadolibre/auth")
public class AuthController {

    private final MeliAuthService meliAuthService; 

    private static AccessTokenResponse currentAccessToken;

    @Autowired
    public AuthController(MeliAuthService meliAuthService) { 
        this.meliAuthService = meliAuthService;
    }

    @GetMapping("/authorization-url")
    public ResponseEntity<Map<String, String>> getAuthorizationUrl() {
        String authorizationUrl = meliAuthService.getAuthorizationUrl();
        return ResponseEntity.ok(Collections.singletonMap("authorizationUrl", authorizationUrl));
    }
    
    @PostMapping("/token-exchange")
    public ResponseEntity<AccessTokenResponse> exchangeCodeForAccessToken(@RequestBody AccessTokenRequest request) {
        try {
            AccessTokenResponse tokenResponse = meliAuthService.exchangeCodeForAccessToken(request.getCode());

            // Guarda el token globalmente para uso en otros servicios (SOLO DEMOSTRACIÓN)
            currentAccessToken = tokenResponse;

            System.out.println("------------------------------------");
            System.out.println("¡Access Token obtenido exitosamente!");
            System.out.println("Access Token: " + tokenResponse.getAccessToken());
            System.out.println("Refresh Token: " + tokenResponse.getRefreshToken());
            System.out.println("Expira en: " + tokenResponse.getExpiresIn() + " segundos");
            System.out.println("ID de Usuario: " + tokenResponse.getUserId());
            System.out.println("------------------------------------");

            return ResponseEntity.ok(tokenResponse);
        } catch (Exception e) {
            System.err.println("Error al obtener el token de acceso: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<AccessTokenResponse> refreshAccessToken(@RequestBody AccessTokenRequest request) {
        if (request.getRefreshToken() == null || request.getRefreshToken().isEmpty()) {
            return ResponseEntity.badRequest().body(null);
        }
        try {
            AccessTokenResponse newToken = meliAuthService.refreshAccessToken(request.getRefreshToken());

            // Actualiza el token almacenado (para demostración)
            currentAccessToken = newToken;

            System.out.println("------------------------------------");
            System.out.println("¡Token de Acceso refrescado exitosamente!");
            System.out.println("Nuevo Access Token: " + newToken.getAccessToken());
            System.out.println("Nuevo Refresh Token: " + newToken.getRefreshToken());
            System.out.println("Expira en: " + newToken.getExpiresIn() + " segundos");
            System.out.println("------------------------------------");

            return ResponseEntity.ok(newToken);
        } catch (Exception e) {
            System.err.println("Error al refrescar el token: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Endpoint para consultar el token actual (SOLO PARA DESARROLLO/DEPURACIÓN).
     * No debe usarse en producción sin autenticación y autorización adecuadas.
     *
     * URL de Acceso: GET http://localhost:8080/api/mercadolibre/auth/current-token
     * @return El token de acceso actual almacenado (si existe).
     */
    @GetMapping("/current-token")
    public ResponseEntity<AccessTokenResponse> getCurrentToken() {
        if (currentAccessToken != null) {
            return ResponseEntity.ok(currentAccessToken);
        }
        return ResponseEntity.notFound().build();
    }

    public static AccessTokenResponse getCurrentAccessTokenForService() {
        return currentAccessToken;
    }
}