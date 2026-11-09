package pe.edu.ucv.cafetin.cuentas.service;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Correo de recuperación y push salen en otro hilo, después del commit: la respuesta no espera al
 * servidor SMTP ni a FCM (hasta 5 s cada uno), no se retiene una de las 5 conexiones a Neon mientras
 * tanto, y pedir recuperación tarda lo mismo exista o no la cuenta. Usa el ejecutor que configura
 * Spring Boot ({@code spring.task.execution}).
 */
@Configuration
@EnableAsync
public class EnviosEnSegundoPlano {
}
