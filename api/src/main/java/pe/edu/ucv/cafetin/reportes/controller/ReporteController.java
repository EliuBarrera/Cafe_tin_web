package pe.edu.ucv.cafetin.reportes.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import pe.edu.ucv.cafetin.comun.dto.Pagina;
import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.reportes.dto.ReportesDto.IngresoPeriodoResponse;
import pe.edu.ucv.cafetin.reportes.dto.ReportesDto.Periodo;
import pe.edu.ucv.cafetin.reportes.dto.ReportesDto.ProductoVendidoResponse;
import pe.edu.ucv.cafetin.reportes.dto.ReportesDto.VentaResponse;

/** Ingresos y ventas (RF-20 a RF-22), solo DUENO (RNF-05). Responsable: Maria Elizabeth Ruiz. */
@RestController
@RequestMapping("/api/reportes")
@Tag(name = "Ingresos", description = "Ventas, ingresos por periodo y productos más vendidos (solo DUENO)")
public class ReporteController {

	@GetMapping("/ingresos")
	@Operation(summary = "Ingresos por día, semana o mes", description = """
			DUENO · RF-21. Solo pagos PAGADO, en hora de Lima. Sin fechas: últimos 30 días.
			Periodos sin ventas no aparecen.""")
	public List<IngresoPeriodoResponse> ingresos(
			@RequestParam(defaultValue = "DIA") Periodo periodo,
			@Parameter(description = "Desde (incluido), yyyy-MM-dd") @RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@Parameter(description = "Hasta (incluido), yyyy-MM-dd") @RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
		throw new ApiException.NoImplementado("RF-21");
	}

	@GetMapping("/mas-vendidos")
	@Operation(summary = "Productos más vendidos", description = "DUENO · RF-22. Ordenados por unidades vendidas, de todo el histórico.")
	public List<ProductoVendidoResponse> masVendidos(
			@RequestParam(defaultValue = "10") @Min(1) @Max(100) int limite) {
		throw new ApiException.NoImplementado("RF-22");
	}

	@GetMapping("/ventas")
	@Operation(summary = "Ventas registradas", description = "DUENO · RF-20. Cada pago PAGADO con su monto; más recientes primero. Sin fechas: últimos 30 días.")
	public Pagina<VentaResponse> ventas(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
			@RequestParam(defaultValue = "0") @Min(0) int pagina,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int tamano) {
		throw new ApiException.NoImplementado("RF-20");
	}

}
