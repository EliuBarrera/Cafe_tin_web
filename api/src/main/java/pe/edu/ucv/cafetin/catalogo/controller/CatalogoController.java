package pe.edu.ucv.cafetin.catalogo.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import pe.edu.ucv.cafetin.catalogo.dto.ProductoCatalogoResponse;
import pe.edu.ucv.cafetin.catalogo.service.CatalogoService;

/** Catálogo con disponibilidad en tiempo real (RF-04). Responsable: Eliú Barrera. */
@RestController
@RequestMapping("/api/catalogo")
@Tag(name = "Pedidos", description = "Catálogo, carrito, pago, recibo e historial del estudiante")
public class CatalogoController {

	private final CatalogoService servicio;

	public CatalogoController(CatalogoService servicio) {
		this.servicio = servicio;
	}

	@GetMapping
	@Operation(summary = "Ver catálogo", description = """
			Autenticado (cualquier rol) · RF-04. Productos activos con su stock; incluye los AGOTADO
			para que la app los muestre deshabilitados. "Tiempo real": la app vuelve a consultar al abrir
			la pantalla y antes de pagar.""")
	public List<ProductoCatalogoResponse> listar(
			@Parameter(description = "Id de categoría para filtrar") @RequestParam(required = false) Integer categoria) {
		return servicio.listar(categoria);
	}

}
