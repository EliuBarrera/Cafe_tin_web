package pe.edu.ucv.cafetin.cafeterias.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import pe.edu.ucv.cafetin.cafeterias.dto.CafeteriaResponse;
import pe.edu.ucv.cafetin.cafeterias.service.CafeteriaService;

/** Cafetines de la plataforma. Responsable: Eliú Barrera. */
@RestController
@RequestMapping("/api/cafeterias")
@Tag(name = "Pedidos")
public class CafeteriaController {

	private final CafeteriaService servicio;

	public CafeteriaController(CafeteriaService servicio) {
		this.servicio = servicio;
	}

	@GetMapping
	@Operation(summary = "Listar cafetines", description = """
			Autenticado (cualquier rol). Cafetines activos por nombre: el estudiante elige uno antes de ver
			el catálogo y todo su pedido es de ese cafetín.""")
	public List<CafeteriaResponse> listar() {
		return servicio.listarActivas();
	}

}
