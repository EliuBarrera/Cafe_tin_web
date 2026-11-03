package pe.edu.ucv.cafetin.cuentas.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.comun.error.CodigoError;
import pe.edu.ucv.cafetin.cuentas.dto.CuentasDto.LoginRequest;
import pe.edu.ucv.cafetin.cuentas.dto.CuentasDto.RegistroEstudianteRequest;
import pe.edu.ucv.cafetin.cuentas.dto.CuentasDto.TokenResponse;
import pe.edu.ucv.cafetin.cuentas.modelo.Estudiante;
import pe.edu.ucv.cafetin.cuentas.modelo.Usuario;
import pe.edu.ucv.cafetin.cuentas.repository.EstudianteRepository;
import pe.edu.ucv.cafetin.cuentas.repository.UsuarioRepository;

/** Registro de estudiantes e inicio de sesión de los tres roles (RF-01, RF-02). */
@Service
public class AuthService {

	private final UsuarioRepository usuarios;
	private final EstudianteRepository estudiantes;
	private final PasswordEncoder encoder;
	private final TokenService tokens;

	public AuthService(UsuarioRepository usuarios, EstudianteRepository estudiantes, PasswordEncoder encoder,
			TokenService tokens) {
		this.usuarios = usuarios;
		this.estudiantes = estudiantes;
		this.encoder = encoder;
		this.tokens = tokens;
	}

	/** Si dos registros iguales llegan a la vez, el UNIQUE de la base decide y responde 409 (ManejadorErrores). */
	@Transactional
	public TokenResponse registrar(RegistroEstudianteRequest solicitud) {
		String correo = ValidacionCuenta.correoNuevo(solicitud.correo());
		String nombre = ValidacionCuenta.nombre(solicitud.nombre());
		String codigo = solicitud.codigo().strip();
		ValidacionCuenta.contrasena(solicitud.contrasena());

		if (usuarios.existsByCorreo(correo)) {
			throw new ApiException(CodigoError.CONFLICTO, "Ya existe una cuenta con ese correo");
		}
		if (estudiantes.existsByCodigo(codigo)) {
			throw new ApiException(CodigoError.CONFLICTO, "Ya existe una cuenta con ese código de estudiante");
		}

		Estudiante estudiante = new Estudiante(nombre, solicitud.apellidos().strip(), correo,
				encoder.encode(solicitud.contrasena()), codigo, solicitud.carrera().strip(), solicitud.ciclo());
		estudiante.setTelefono(solicitud.telefono());
		return tokens.emitir(usuarios.save(estudiante));
	}

	/** El mismo mensaje para correo inexistente, contraseña errónea o cuenta inactiva. */
	@Transactional(readOnly = true)
	public TokenResponse login(LoginRequest solicitud) {
		Usuario usuario = usuarios.findByCorreo(ValidacionCuenta.normalizarCorreo(solicitud.correo()))
				.filter(Usuario::isActivo)
				.filter(u -> encoder.matches(solicitud.contrasena(), u.getContrasena()))
				.orElseThrow(() -> new ApiException(CodigoError.NO_AUTENTICADO, "Correo o contraseña incorrectos"));
		return tokens.emitir(usuario);
	}

}
