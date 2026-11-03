package pe.edu.ucv.cafetin.cuentas.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.comun.error.CodigoError;
import pe.edu.ucv.cafetin.cuentas.dto.CuentasDto.UsuarioResponse;
import pe.edu.ucv.cafetin.cuentas.modelo.Usuario;
import pe.edu.ucv.cafetin.cuentas.repository.UsuarioRepository;

@Service
public class UsuarioService {

	private final UsuarioRepository usuarios;

	public UsuarioService(UsuarioRepository usuarios) {
		this.usuarios = usuarios;
	}

	/** El token puede sobrevivir a su usuario: si ya no existe o está inactivo, hay que volver a entrar. */
	@Transactional(readOnly = true)
	public UsuarioResponse perfil(Integer id) {
		return usuarios.findById(id)
				.filter(Usuario::isActivo)
				.map(UsuarioService::aRespuesta)
				.orElseThrow(() -> new ApiException(CodigoError.NO_AUTENTICADO, "Inicia sesión para continuar"));
	}

	static UsuarioResponse aRespuesta(Usuario u) {
		return new UsuarioResponse(u.getId(), u.getNombre(), u.getApellidos(), u.getCorreo(), u.getTipoUsuario());
	}

}
