package pe.edu.ucv.cafetin.inventario.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.inventario.dto.InventarioDto.AlertaResponse;

/** Alertas de stock que genera la base (RF-17). Responsable: Maria Elizabeth Ruiz. */
@RestController
@RequestMapping("/api/alertas")
@Tag(name = "Inventario")
public class AlertaController {

	@GetMapping
	@Operation(summary = "Listar alertas de stock", description = """
			DUENO · RF-17. Más recientes primero, con el teléfono del proveedor.
			El panel consulta cada 5 s para el contador de alertas.""")
	public List<AlertaResponse> listar(
			@Parameter(description = "false = solo pendientes (por defecto); true = solo atendidas")
			@RequestParam(defaultValue = "false") boolean atendida) {
		throw new ApiException.NoImplementado("RF-17");
	}

	@PatchMapping("/{id}/atendida")
	@Operation(summary = "Marcar alerta como atendida", description = "DUENO · RF-17")
	public AlertaResponse marcarAtendida(@PathVariable Integer id) {
		throw new ApiException.NoImplementado("RF-17");
	}

}
