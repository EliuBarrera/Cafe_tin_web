package pe.edu.ucv.cafetin.cuentas.seguridad;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.comun.error.CodigoError;
import pe.edu.ucv.cafetin.cuentas.repository.UsuarioRepository;

/**
 * Id del usuario autenticado: el {@code sub} del JWT. Para usarlo con {@code @WithMockUser} en un
 * test, indica el id como nombre: {@code @WithMockUser(username = "4", roles = "ESTUDIANTE")}.
 */
@Component
public class UsuarioActual {

	private final UsuarioRepository usuarios;

	public UsuarioActual(UsuarioRepository usuarios) {
		this.usuarios = usuarios;
	}

	public Integer id() {
		return Integer.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
	}

	/**
	 * Cafetería del dueño o trabajador autenticado. Se lee de la base (no del JWT) para que funcione
	 * igual con {@code @WithMockUser}. Un estudiante no tiene cafetería: 403.
	 */
	public Integer cafeteria() {
		return usuarios.cafeteriaDe(id())
				.orElseThrow(() -> new ApiException(CodigoError.ACCESO_DENEGADO, "El usuario no pertenece a una cafetería"));
	}

}
