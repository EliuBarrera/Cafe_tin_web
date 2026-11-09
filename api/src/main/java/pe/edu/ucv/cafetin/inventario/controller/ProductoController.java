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
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import pe.edu.ucv.cafetin.comun.dto.EstadoRequest;
import pe.edu.ucv.cafetin.comun.error.ErrorRespuesta;
import pe.edu.ucv.cafetin.inventario.dto.InventarioDto.InventarioResponse;
import pe.edu.ucv.cafetin.inventario.dto.InventarioDto.ProductoActualizarRequest;
import pe.edu.ucv.cafetin.inventario.dto.InventarioDto.ProductoRequest;
import pe.edu.ucv.cafetin.inventario.dto.InventarioDto.ProductoResponse;
import pe.edu.ucv.cafetin.inventario.dto.InventarioDto.RecargaRequest;
import pe.edu.ucv.cafetin.inventario.service.ProductoService;

/** Productos e inventario del dueño (RF-15, RF-18, RF-19). Responsable: Eliú Barrera. */
@RestController
@RequestMapping("/api")
@Tag(name = "Inventario", description = "Productos, stock, alertas, categorías y proveedores (solo DUENO)")
public class ProductoController {

	private final ProductoService servicio;

	public ProductoController(ProductoService servicio) {
		this.servicio = servicio;
	}

	@GetMapping("/productos")
	@Operation(summary = "Listar productos con su stock", description = "DUENO · RF-15, RF-19. Incluye los deshabilitados.")
	public List<ProductoResponse> listar(
			@Parameter(description = "true = solo activos; false = solo deshabilitados; vacío = todos")
			@RequestParam(required = false) Boolean activo) {
		return servicio.listar(activo);
	}

	@GetMapping("/productos/{id}")
	@Operation(summary = "Ver producto", description = "DUENO · RF-19")
	public ProductoResponse obtener(@PathVariable Integer id) {
		return servicio.obtener(id);
	}

	@PostMapping("/productos")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Registrar producto con stock inicial", description = "DUENO · RF-15. Producto e inventario en la misma transacción.")
	@ApiResponse(responseCode = "409", description = "Ya existe un producto con ese nombre (CONFLICTO)",
			content = @Content(schema = @Schema(implementation = ErrorRespuesta.class)))
	public ProductoResponse registrar(@Valid @RequestBody ProductoRequest solicitud) {
		return servicio.registrar(solicitud);
	}

	@PutMapping("/productos/{id}")
	@Operation(summary = "Editar producto", description = "DUENO · RF-19. Cambiar el precio no afecta pedidos anteriores (precio histórico).")
	@ApiResponse(responseCode = "409", description = "Ya existe un producto con ese nombre (CONFLICTO)",
			content = @Content(schema = @Schema(implementation = ErrorRespuesta.class)))
	public ProductoResponse actualizar(@PathVariable Integer id, @Valid @RequestBody ProductoActualizarRequest solicitud) {
		return servicio.actualizar(id, solicitud);
	}

	@PatchMapping("/productos/{id}/estado")
	@Operation(summary = "Habilitar o deshabilitar producto", description = "DUENO · RF-19. Deshabilitado = oculto del catálogo, sin borrarlo.")
	public ProductoResponse cambiarEstado(@PathVariable Integer id, @Valid @RequestBody EstadoRequest solicitud) {
		return servicio.cambiarEstado(id, solicitud.activo());
	}

	@PatchMapping("/inventario/{idProducto}/recarga")
	@Operation(summary = "Recargar stock", description = "DUENO · RF-18. Suma unidades y actualiza la fecha; marca como atendidas las alertas abiertas del producto.")
	public InventarioResponse recargar(@PathVariable Integer idProducto, @Valid @RequestBody RecargaRequest solicitud) {
		return servicio.recargar(idProducto, solicitud.cantidad());
	}

}
