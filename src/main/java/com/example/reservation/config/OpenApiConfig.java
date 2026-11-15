package com.example.reservation.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentacion OpenAPI de la API. Se publica en /v3/api-docs y la UI interactiva
 * en /swagger-ui.html (publicas, para poder probar la API sin desplegar nada).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI reservationOpenApi() {
        final String scheme = "basicAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("API de reservas de salas")
                        .version("1.0.0")
                        .description("""
                                API REST para reservar salas de reuniones.

                                Reglas de negocio:
                                - La lectura (GET) es publica.
                                - Crear sala, crear reserva y cancelar reserva exigen credenciales (HTTP Basic).
                                - Si la sala ya esta ocupada en el horario solicitado se responde 409.
                                - Los datos invalidos se responden 400 con el detalle por campo en `fieldErrors`.
                                - El solapamiento es estricto: una reserva que termina a las 10:00 y otra que
                                  empieza a las 10:00 son compatibles (no se solapan).

                                Credenciales de la demo: admin/admin123 (crear salas) y user/user123 (reservas).
                                """)
                        .contact(new Contact().name("Miguel Pina")))
                .components(new Components().addSecuritySchemes(scheme,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("Credenciales de la demo: admin/admin123 o user/user123")))
                .addSecurityItem(new SecurityRequirement().addList(scheme));
    }
}
