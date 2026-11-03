package pe.edu.ucv.cafetin.cuentas.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.comun.error.CodigoError;
import pe.edu.ucv.cafetin.cuentas.dto.CuentasDto.TrabajadorRequest;
import pe.edu.ucv.cafetin.cuentas.dto.CuentasDto.TrabajadorResponse;
import pe.edu.ucv.cafetin.cuentas.modelo.Trabajador;
import pe.edu.ucv.cafetin.cuentas.repository.TrabajadorRepository;
import pe.edu.ucv.cafetin.cuentas.repository.UsuarioRepository;

/** Gestión de trabajadores por el dueño (RF-02). */
@Service
public class TrabajadorService {

	private final TrabajadorRepository trabajadores;
	private final UsuarioRepository usuarios;
	private final PasswordEncoder encoder;

	public TrabajadorService(TrabajadorRepository trabajadores, UsuarioRepository usuarios, PasswordEncoder encoder) {
		this.trabajadores = trabajadores;
		this.usuarios = usuarios;
		this.encoder = encoder;
	}

	@Transactional(readOnly = true)
	public List<TrabajadorResponse> listar() {
		return trabajadores.findAll(Sort.by("apellidos", "nombre")).stream()
				.map(TrabajadorService::aRespuesta)
				.toList();
	}

	@Transactional
	public TrabajadorResponse registrar(TrabajadorRequest solicitud) {
		String correo = ValidacionCuenta.correoNuevo(solicitud.correo());
		String nombre = ValidacionCuenta.nombre(solicitud.nombre());
		String codigo = solicitud.codigoEmpleado().strip();
		ValidacionCuenta.contrasena(solicitud.contrasena());

		if (usuarios.existsByCorreo(correo)) {
			throw new ApiException(CodigoError.CONFLICTO, "Ya existe una cuenta con ese correo");
		}
		if (trabajadores.existsByCodigoEmpleado(codigo)) {
			throw new ApiException(CodigoError.CONFLICTO, "Ya existe un trabajador con ese código de empleado");
		}

		Trabajador trabajador = new Trabajador(nombre, solicitud.apellidos().strip(), correo,
				encoder.encode(solicitud.contrasena()), codigo, solicitud.turno());
		trabajador.setTelefono(solicitud.telefono());
		return aRespuesta(trabajadores.saveAndFlush(trabajador));
	}

	@Transactional
	public TrabajadorResponse cambiarEstado(Integer id, boolean activo) {
		Trabajador trabajador = trabajadores.findById(id)
				.orElseThrow(() -> new ApiException.NoEncontrado("El trabajador no existe"));
		trabajador.setActivo(activo);
		return aRespuesta(trabajador);
	}

	private static TrabajadorResponse aRespuesta(Trabajador t) {
		return new TrabajadorResponse(t.getId(), t.getNombre(), t.getApellidos(), t.getCorreo(), t.getTelefono(),
				t.getCodigoEmpleado(), t.getTurno(), t.getFechaIngreso(), t.isActivo());
	}

}
