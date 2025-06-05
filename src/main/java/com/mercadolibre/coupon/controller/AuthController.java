package com.mercadolibre.coupon.controller;

import com.mercadolibre.coupon.dto.AccessTokenRequest;
import com.mercadolibre.coupon.dto.AccessTokenResponse;
import com.mercadolibre.coupon.service.MeliAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/coupon/api/mercadolibre/auth")
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
    
    /**
     * Callback endpoint para manejar la redirección de MercadoLibre después de la autorización.
     * MercadoLibre redirige aquí con el código de autorización o error.
     * 
     * URL de Callback: GET http://localhost:8080/api/mercadolibre/auth/callback
     * 
     * @param code Código de autorización enviado por MercadoLibre (si la autorización fue exitosa)
     * @param error Código de error enviado por MercadoLibre (si hubo un error)
     * @param errorDescription Descripción del error (opcional)
     * @param state Parámetro de estado para prevenir ataques CSRF (opcional)
     * @return ResponseEntity con el resultado del proceso de autorización
     */
    @GetMapping("/callback")
    public ResponseEntity<Map<String, Object>> handleCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDescription,
            @RequestParam(value = "state", required = false) String state) {
        
        System.out.println("------------------------------------");
        System.out.println("Callback recibido de MercadoLibre");
        System.out.println("Code: " + code);
        System.out.println("Error: " + error);
        System.out.println("Error Description: " + errorDescription);
        System.out.println("State: " + state);
        System.out.println("------------------------------------");

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", System.currentTimeMillis());

        // Si hay un error en la autorización
        if (error != null) {
            System.err.println("Error en la autorización: " + error + " - " + errorDescription);
            response.put("success", false);
            response.put("error", error);
            response.put("errorDescription", errorDescription);
            response.put("message", "Error en la autorización de MercadoLibre");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // Si no hay código, es un error
        if (code == null || code.isEmpty()) {
            System.err.println("No se recibió código de autorización");
            response.put("success", false);
            response.put("error", "no_code");
            response.put("message", "No se recibió código de autorización de MercadoLibre");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        try {
            // Intercambia el código por el access token
            AccessTokenResponse tokenResponse = meliAuthService.exchangeCodeForAccessToken(code);
            
            // Guarda el token globalmente para uso en otros servicios (SOLO DEMOSTRACIÓN)
            currentAccessToken = tokenResponse;

            System.out.println("------------------------------------");
            System.out.println("¡Access Token obtenido exitosamente desde callback!");
            System.out.println("Access Token: " + tokenResponse.getAccessToken());
            System.out.println("Refresh Token: " + tokenResponse.getRefreshToken());
            System.out.println("Expira en: " + tokenResponse.getExpiresIn() + " segundos");
            System.out.println("ID de Usuario: " + tokenResponse.getUserId());
            System.out.println("------------------------------------");

            response.put("success", true);
            response.put("message", "Autorización completada exitosamente");
            response.put("userId", tokenResponse.getUserId());
            response.put("expiresIn", tokenResponse.getExpiresIn());
            response.put("tokenObtained", true);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Error al procesar el callback: " + e.getMessage());
            e.printStackTrace();
            
            response.put("success", false);
            response.put("error", "token_exchange_failed");
            response.put("message", "Error al intercambiar código por token: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    @PostMapping("/token-exchange")
    public ResponseEntity<AccessTokenResponse> exchangeCodeForAccessToken(@RequestBody AccessTokenRequest request) {
        try {
            AccessTokenResponse tokenResponse = meliAuthService.exchangeCodeForAccessToken(request.getCode());
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