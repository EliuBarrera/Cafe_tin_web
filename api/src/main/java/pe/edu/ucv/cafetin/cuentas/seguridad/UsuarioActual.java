package pe.edu.ucv.cafetin.cuentas.seguridad;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Id del usuario autenticado: el {@code sub} del JWT. Para usarlo con {@code @WithMockUser} en un
 * test, indica el id como nombre: {@code @WithMockUser(username = "4", roles = "ESTUDIANTE")}.
 */
@Component
public class UsuarioActual {

	public Integer id() {
		return Integer.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
	}

}
