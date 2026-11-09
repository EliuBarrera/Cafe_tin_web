package pe.edu.ucv.cafetin.cuentas.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ucv.cafetin.cuentas.modelo.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

	Optional<Usuario> findByCorreo(String correo);

	boolean existsByCorreo(String correo);

	boolean existsByIdAndActivoTrue(Integer id);

	@Modifying
	@Query(value = "UPDATE usuario SET fcm_token = :token WHERE id_usuario = :id", nativeQuery = true)
	void guardarFcmToken(@Param("id") Integer id, @Param("token") String token);

	@Modifying
	@Query(value = "UPDATE usuario SET fcm_token = NULL WHERE fcm_token = :token AND id_usuario <> :id", nativeQuery = true)
	void quitarFcmTokenDeOtros(@Param("token") String token, @Param("id") Integer id);

	/**
	 * Borra un token que el servicio de push rechazó, solo si sigue siendo el mismo: la app pudo haber
	 * registrado uno nuevo mientras se enviaba el aviso. Se llama después del commit del despacho, por eso
	 * abre su propia transacción.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@Modifying
	@Query(value = "UPDATE usuario SET fcm_token = NULL WHERE id_usuario = :id AND fcm_token = :token", nativeQuery = true)
	void quitarFcmToken(@Param("id") Integer id, @Param("token") String token);

	/** La expiración la calcula la base: {@code token_expira} está en hora de Lima, no la de la JVM. */
	@Modifying
	@Query(value = """
			UPDATE usuario
			SET token_recupera = :hash, token_expira = LOCALTIMESTAMP + make_interval(mins => :minutos)
			WHERE id_usuario = :id""", nativeQuery = true)
	void guardarTokenRecuperacion(@Param("id") Integer id, @Param("hash") String hash, @Param("minutos") int minutos);

	/**
	 * Cambia la contraseña y consume el token en una sola sentencia, para que dos peticiones
	 * simultáneas no puedan usar el mismo token. Devuelve 0 si es inválido o expiró.
	 */
	@Modifying
	@Query(value = """
			UPDATE usuario
			SET contrasena = :contrasena, token_recupera = NULL, token_expira = NULL
			WHERE token_recupera = :hash AND token_expira > LOCALTIMESTAMP AND activo""", nativeQuery = true)
	int restablecerContrasena(@Param("hash") String hash, @Param("contrasena") String contrasenaHash);

}
