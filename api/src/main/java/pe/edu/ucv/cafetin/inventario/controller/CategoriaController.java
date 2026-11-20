package pe.edu.ucv.cafetin.inventario.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import pe.edu.ucv.cafetin.cuentas.seguridad.UsuarioActual;
import pe.edu.ucv.cafetin.comun.dto.EstadoRequest;
import pe.edu.ucv.cafetin.inventario.dto.InventarioDto.CategoriaRequest;
import pe.edu.ucv.cafetin.inventario.dto.InventarioDto.CategoriaResponse;
import pe.edu.ucv.cafetin.inventario.service.CategoriaProveedorService;

/**
 * Categorías: lectura para todos (filtro del catálogo, RF-04); escritura solo DUENO (RF-15).
 * Responsable: Eliú Barrera.
 */
@RestController
@RequestMapping("/api/categorias")
@Tag(name = "Inventario")
public class CategoriaController {

	private final CategoriaProveedorService servicio;

	private final UsuarioActual usuarioActual;

	public CategoriaController(CategoriaProveedorService servicio, UsuarioActual usuarioActual) {
		this.servicio = servicio;
		this.usuarioActual = usuarioActual;
	}

	@GetMapping
	@Operation(summary = "Listar categorías activas", description = """
			Autenticado (cualquier rol) · RF-04. Para los filtros del catálogo. El estudiante indica el cafetín;
			el dueño y el trabajador ven las de su cafetería si lo omiten.""")
	public List<CategoriaResponse> listar(
			@Parameter(description = "Id del cafetín; obligatorio para el estudiante") @RequestParam(required = false) Integer cafeteria) {
		return servicio.listarCategorias(cafeteria != null ? cafeteria : usuarioActual.cafeteria());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Crear categoría", description = "DUENO · RF-15. 409 CONFLICTO si el nombre ya existe.")
	public CategoriaResponse crear(@Valid @RequestBody CategoriaRequest solicitud) {
		return servicio.crearCategoria(usuarioActual.cafeteria(), solicitud);
	}

	@PutMapping("/{id}")
	@Operation(summary = "Editar categoría", description = "DUENO · RF-15")
	public CategoriaResponse actualizar(@PathVariable Integer id, @Valid @RequestBody CategoriaRequest solicitud) {
		return servicio.actualizarCategoria(usuarioActual.cafeteria(), id, solicitud);
	}

	@PatchMapping("/{id}/estado")
	@Operation(summary = "Activar o desactivar categoría", description = "DUENO · RF-15")
	public CategoriaResponse cambiarEstado(@PathVariable Integer id, @Valid @RequestBody EstadoRequest solicitud) {
		return servicio.cambiarEstadoCategoria(usuarioActual.cafeteria(), id, solicitud.activo());
	}

}
