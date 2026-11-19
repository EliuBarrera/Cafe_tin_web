package pe.edu.ucv.cafetin.mercadopago.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Cuenta de Mercado Pago de cada cafetín (columnas {@code mp_*} de {@code cafeteria}, V13) y el
 * {@code state} del OAuth. Con SQL y fuera de la entidad {@code Cafeteria}: los tokens no viajan con
 * ella y no pueden terminar en un DTO por descuido. Las fechas las pone la base (hora de Lima).
 */
@Repository
public class CuentaMercadoPagoRepository {

	private final JdbcTemplate jdbc;

	public CuentaMercadoPagoRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Tokens cifrados tal como están guardados. */
	public record Cuenta(long idUsuarioMp, String accessToken, String refreshToken, LocalDateTime expira,
			LocalDateTime conectadoEn, boolean porVencer) {
	}

	public Optional<Cuenta> de(int idCafeteria) {
		List<Cuenta> filas = jdbc.query("""
				SELECT mp_user_id, mp_access_token, mp_refresh_token, mp_token_expira, mp_conectado_en,
				       mp_token_expira IS NOT NULL AND mp_token_expira < LOCALTIMESTAMP + INTERVAL '7 days' AS por_vencer
				FROM cafeteria WHERE id_cafeteria = ? AND mp_access_token IS NOT NULL""",
				(rs, i) -> new Cuenta(rs.getLong("mp_user_id"), rs.getString("mp_access_token"),
						rs.getString("mp_refresh_token"),
						rs.getObject("mp_token_expira", LocalDateTime.class),
						rs.getObject("mp_conectado_en", LocalDateTime.class), rs.getBoolean("por_vencer")),
				idCafeteria);
		return filas.stream().findFirst();
	}

	/**
	 * Guarda tokens ya cifrados. {@code conexionNueva}: el dueño acaba de autorizar (la fecha de
	 * conexión pasa a ser ahora); si es una renovación, se conserva la fecha original.
	 */
	public void guardar(int idCafeteria, long idUsuarioMp, String accessToken, String refreshToken,
			long expiraEnSegundos, boolean conexionNueva) {
		jdbc.update("""
				UPDATE cafeteria SET mp_user_id = ?, mp_access_token = ?, mp_refresh_token = ?,
				       mp_token_expira = LOCALTIMESTAMP + make_interval(secs => ?),
				       mp_conectado_en = CASE WHEN ? OR mp_conectado_en IS NULL THEN LOCALTIMESTAMP ELSE mp_conectado_en END
				WHERE id_cafeteria = ?""",
				idUsuarioMp, accessToken, refreshToken, (double) expiraEnSegundos, conexionNueva, idCafeteria);
	}

	public void borrar(int idCafeteria) {
		jdbc.update("""
				UPDATE cafeteria SET mp_user_id = NULL, mp_access_token = NULL, mp_refresh_token = NULL,
				       mp_token_expira = NULL, mp_conectado_en = NULL
				WHERE id_cafeteria = ?""", idCafeteria);
	}

	// ------------------------------------------------------------------ state del OAuth

	public void guardarEstado(String estado, int idCafeteria) {
		jdbc.update("DELETE FROM oauth_estado WHERE expira < LOCALTIMESTAMP");
		jdbc.update("INSERT INTO oauth_estado (estado, id_cafeteria, expira) VALUES (?, ?, LOCALTIMESTAMP + INTERVAL '10 minutes')",
				estado, idCafeteria);
	}

	/** Lo borra al usarlo (un solo uso). Vacío si no existe o ya venció. */
	public Optional<Integer> consumirEstado(String estado) {
		List<Integer> filas = jdbc.query("""
				DELETE FROM oauth_estado WHERE estado = ? RETURNING id_cafeteria, expira >= LOCALTIMESTAMP AS vigente""",
				(rs, i) -> rs.getBoolean("vigente") ? rs.getInt("id_cafeteria") : null, estado);
		return filas.stream().filter(id -> id != null).findFirst();
	}

}
