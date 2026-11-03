package pe.edu.ucv.cafetin.cuentas.service;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import pe.edu.ucv.cafetin.cuentas.dto.CuentasDto.TokenResponse;
import pe.edu.ucv.cafetin.cuentas.modelo.Usuario;
import pe.edu.ucv.cafetin.cuentas.seguridad.SecurityConfig;

/** Emite el JWT: {@code sub} = id del usuario, {@code rol} = su tipo. */
@Service
public class TokenService {

	private final JwtEncoder encoder;
	private final long expiracionSegundos;

	public TokenService(JwtEncoder encoder, @Value("${cafetin.jwt.expiracion-segundos}") long expiracionSegundos) {
		this.encoder = encoder;
		this.expiracionSegundos = expiracionSegundos;
	}

	public TokenResponse emitir(Usuario usuario) {
		Instant ahora = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(String.valueOf(usuario.getId()))
				.issuedAt(ahora)
				.expiresAt(ahora.plusSeconds(expiracionSegundos))
				.claim(SecurityConfig.CLAIM_ROL, usuario.getTipoUsuario().name())
				.build();
		String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
		return new TokenResponse(token, expiracionSegundos, UsuarioService.aRespuesta(usuario));
	}

}
