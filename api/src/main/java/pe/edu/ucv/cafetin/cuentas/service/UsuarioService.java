package pe.edu.ucv.cafetin.cuentas.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ucv.cafetin.cafeterias.dto.CafeteriaResponse;
import pe.edu.ucv.cafetin.cafeterias.repository.CafeteriaRepository;
import pe.edu.ucv.cafetin.cafeterias.service.CafeteriaService;
import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.comun.error.CodigoError;
import pe.edu.ucv.cafetin.cuentas.dto.CuentasDto.UsuarioResponse;
import pe.edu.ucv.cafetin.cuentas.modelo.TipoUsuario;
import pe.edu.ucv.cafetin.cuentas.modelo.Usuario;
import pe.edu.ucv.cafetin.cuentas.repository.UsuarioRepository;

@Service
public class UsuarioService {

	private final UsuarioRepository usuarios;
	private final CafeteriaRepository cafeterias;

	public UsuarioService(UsuarioRepository usuarios, CafeteriaRepository cafeterias) {
		this.usuarios = usuarios;
		this.cafeterias = cafeterias;
	}

	/** El token puede sobrevivir a su usuario: si ya no existe o está inactivo, hay que volver a entrar. */
	@Transactional(readOnly = true)
	public UsuarioResponse perfil(Integer id) {
		return usuarios.findById(id)
				.filter(Usuario::isActivo)
				.map(this::aRespuesta)
				.orElseThrow(() -> new ApiException(CodigoError.NO_AUTENTICADO, "Inicia sesión para continuar"));
	}

	/** Con su cafetería si es dueño o trabajador (el panel muestra su nombre); nula para un estudiante. */
	UsuarioResponse aRespuesta(Usuario u) {
		Integer idCafeteria = u.getTipoUsuario() == TipoUsuario.ESTUDIANTE ? null
				: usuarios.cafeteriaDe(u.getId()).orElse(null);
		CafeteriaResponse cafeteria = idCafeteria == null ? null
				: cafeterias.findById(idCafeteria).map(CafeteriaService::aRespuesta).orElse(null);
		return new UsuarioResponse(u.getId(), u.getNombre(), u.getApellidos(), u.getCorreo(), u.getTipoUsuario(),
				cafeteria);
	}

}
