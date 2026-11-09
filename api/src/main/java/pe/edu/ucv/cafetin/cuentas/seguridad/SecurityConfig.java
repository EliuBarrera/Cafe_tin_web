package pe.edu.ucv.cafetin.cuentas.seguridad;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import jakarta.servlet.DispatcherType;
import pe.edu.ucv.cafetin.cuentas.repository.UsuarioRepository;

/**
 * Seguridad de la API: sin sesión, JWT firmado con HS256 y reglas por ruta (RNF-05).
 * Una regla nueva se pide por PR al responsable del módulo Cuentas.
 */
@Configuration
public class SecurityConfig {

	/** Mínimo para HS256 (RFC 7518, 3.2). */
	private static final int BYTES_MINIMOS_SECRETO = 32;

	/** Claim del JWT con el rol ({@code TipoUsuario}). */
	public static final String CLAIM_ROL = "rol";

	@Bean
	SecurityFilterChain seguridad(HttpSecurity http, RespuestasSeguridad respuestas, UsuarioRepository usuarios)
			throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable) // sin cookies ni sesión: el JWT va en un encabezado
				.cors(cors -> { })
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(r -> r
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers("/api/auth/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll() // Render
						// springdoc los desactiva en el perfil prod.
						.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()

						.requestMatchers("/api/pedidos/**").hasRole("ESTUDIANTE")
						.requestMatchers("/api/despacho/**").hasAnyRole("TRABAJADOR", "DUENO")
						.requestMatchers("/api/inventario/**", "/api/productos/**", "/api/alertas/**",
								"/api/reportes/**", "/api/trabajadores/**", "/api/proveedores/**").hasRole("DUENO")

						.requestMatchers(HttpMethod.GET, "/api/catalogo", "/api/categorias").authenticated()
						.requestMatchers("/api/categorias/**").hasRole("DUENO")
						.requestMatchers("/api/usuarios/me/**").authenticated()
						.anyRequest().denyAll())
				.oauth2ResourceServer(o -> o
						.bearerTokenResolver(resolverDeToken())
						.jwt(j -> j.jwtAuthenticationConverter(convertidorDeUsuario(usuarios)))
						.authenticationEntryPoint(respuestas)
						.accessDeniedHandler(respuestas))
				.exceptionHandling(e -> e
						.authenticationEntryPoint(respuestas)
						.accessDeniedHandler(respuestas))
				.build();
	}

	/**
	 * Las rutas públicas ignoran el encabezado Authorization. La app lo añade a toda petición, y con
	 * un token vencido el filtro respondería 401 incluso al intentar iniciar sesión de nuevo.
	 */
	private static BearerTokenResolver resolverDeToken() {
		DefaultBearerTokenResolver porDefecto = new DefaultBearerTokenResolver();
		return peticion -> peticion.getRequestURI().startsWith("/api/auth/") ? null : porDefecto.resolve(peticion);
	}

	/**
	 * Toma el rol del token y rechaza al usuario desactivado o eliminado: sin esta consulta, un
	 * trabajador dado de baja seguiría entrando hasta que venza su token (hasta 8 horas).
	 */
	private static Converter<Jwt, AbstractAuthenticationToken> convertidorDeUsuario(UsuarioRepository usuarios) {
		JwtGrantedAuthoritiesConverter autoridades = new JwtGrantedAuthoritiesConverter();
		autoridades.setAuthoritiesClaimName(CLAIM_ROL);
		autoridades.setAuthorityPrefix("ROLE_");

		JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
		convertidor.setJwtGrantedAuthoritiesConverter(autoridades);

		return jwt -> {
			if (!usuarios.existsByIdAndActivoTrue(Integer.valueOf(jwt.getSubject()))) {
				throw new DisabledException("La cuenta ya no está activa");
			}
			return convertidor.convert(jwt);
		};
	}

	@Bean
	SecretKey claveJwt(@Value("${cafetin.jwt.secreto}") String secreto) {
		byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < BYTES_MINIMOS_SECRETO) {
			throw new IllegalStateException("JWT_SECRET debe tener al menos " + BYTES_MINIMOS_SECRETO
					+ " bytes. Genera uno con: openssl rand -base64 48 (ver .env.example)");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey claveJwt) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(claveJwt));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey claveJwt) {
		return NimbusJwtDecoder.withSecretKey(claveJwt).macAlgorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(@Value("${cafetin.cors.origenes}") List<String> origenes) {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(origenes);
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));

		UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
		fuente.registerCorsConfiguration("/api/**", cors);
		return fuente;
	}

}
