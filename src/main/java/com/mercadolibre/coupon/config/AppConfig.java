package com.mercadolibre.coupon.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate; // Importación para RestTemplate
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AppConfig {

	@Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // Configuración para WebClient, aunque MeliItemService ya usa WebClient.Builder
    // Esta es solo una buena práctica si quieres un WebClient preconfigurado en otros lugares.
    // MeliItemService.java ya lo construye directamente, pero si lo necesitaras aquí:
    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        // Puedes configurar el base URL y headers si es el WebClient principal
        // Por ahora, MeliItemService lo configura internamente.
        return builder.build();
    }

}
