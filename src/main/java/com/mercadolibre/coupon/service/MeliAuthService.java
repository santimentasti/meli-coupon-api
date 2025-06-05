package com.mercadolibre.coupon.service;

import com.mercadolibre.coupon.dto.AccessTokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class MeliAuthService { // <-- ¡Nombre de clase corregido a MeliAuthService!

    @Value("${meli.client-id}")
    private String clientId;
    @Value("${meli.client-secret}")
    private String clientSecret;
    @Value("${meli.redirect-uri}") // Esta es la URL de TU API a la que ML redirigirá
    private String redirectUri;
    @Value("${meli.auth-url}")
    private String authUrl;
    @Value("${meli.token-url}")
    private String tokenUrl;

    private final RestTemplate restTemplate;

    public MeliAuthService(RestTemplate restTemplate) { // <-- Constructor con el nombre de clase correcto
        this.restTemplate = restTemplate;
    }

    /**
     * Genera la URL de autorización de Mercado Libre.
     * Esta URL debe ser abierta en un navegador por el usuario (probador)
     * para iniciar el proceso de autorización. La API la expone para su uso manual.
     *
     * @return La URL completa de autorización de Mercado Libre.
     */
    public String getAuthorizationUrl() {
        return UriComponentsBuilder.fromUriString(authUrl)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .toUriString();
    }

    /**
     * Intercambia el código de autorización por un Access Token y Refresh Token de Mercado Libre.
     * El código es obtenido por el usuario (probador) de la URL de redirección (callback) de meli-coupon-api
     * y luego enviado a un endpoint de meli-coupon-api.
     * Esta es una llamada de servidor a servidor (desde meli-coupon-api a la API de Mercado Libre).
     *
     * @param code El código de autorización recibido por tu API.
     * @return Un objeto AccessTokenResponse con los tokens.
     */
    public AccessTokenResponse exchangeCodeForAccessToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", code);
        body.add("redirect_uri", redirectUri); // Debe coincidir con lo configurado en ML Developers

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        return restTemplate.postForObject(tokenUrl, request, AccessTokenResponse.class);
    }

    /**
     * Refresca un Access Token utilizando el Refresh Token.
     * Esto permite obtener un nuevo Access Token sin requerir una nueva autorización del usuario.
     * Esta es una llamada de servidor a servidor (desde la meli-coupon-api a la API de Mercado Libre).
     *
     * @param refreshToken El refresh token para obtener un nuevo Access Token.
     * @return Un objeto AccessTokenResponse con los nuevos tokens.
     */
    public AccessTokenResponse refreshAccessToken(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        return restTemplate.postForObject(tokenUrl, request, AccessTokenResponse.class);
    }
}