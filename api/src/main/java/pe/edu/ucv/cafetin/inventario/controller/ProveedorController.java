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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import pe.edu.ucv.cafetin.comun.dto.EstadoRequest;
import pe.edu.ucv.cafetin.inventario.dto.InventarioDto.ProveedorRequest;
import pe.edu.ucv.cafetin.inventario.dto.InventarioDto.ProveedorResponse;
import pe.edu.ucv.cafetin.inventario.service.CategoriaProveedorService;

/** Proveedores (solo DUENO). Responsable: Eliú Barrera. */
@RestController
@RequestMapping("/api/proveedores")
@Tag(name = "Inventario")
public class ProveedorController {

	private final CategoriaProveedorService servicio;

	public ProveedorController(CategoriaProveedorService servicio) {
		this.servicio = servicio;
	}

	@GetMapping
	@Operation(summary = "Listar proveedores", description = "DUENO · RF-15")
	public List<ProveedorResponse> listar() {
		return servicio.listarProveedores();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Registrar proveedor", description = "DUENO · RF-15. 409 CONFLICTO si el nombre ya existe.")
	public ProveedorResponse crear(@Valid @RequestBody ProveedorRequest solicitud) {
		return servicio.crearProveedor(solicitud);
	}

	@PutMapping("/{id}")
	@Operation(summary = "Editar proveedor", description = "DUENO · RF-15")
	public ProveedorResponse actualizar(@PathVariable Integer id, @Valid @RequestBody ProveedorRequest solicitud) {
		return servicio.actualizarProveedor(id, solicitud);
	}

	@PatchMapping("/{id}/estado")
	@Operation(summary = "Activar o desactivar proveedor", description = "DUENO · RF-15")
	public ProveedorResponse cambiarEstado(@PathVariable Integer id, @Valid @RequestBody EstadoRequest solicitud) {
		return servicio.cambiarEstadoProveedor(id, solicitud.activo());
	}

}
