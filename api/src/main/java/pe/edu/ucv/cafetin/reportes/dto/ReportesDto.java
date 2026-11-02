package pe.edu.ucv.cafetin.reportes.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.edu.ucv.cafetin.pedidos.modelo.MetodoPago;

/** DTOs del paquete Ingresos (RF-20 a RF-22). Parte del contrato: no los cambies sin avisar. */
public final class ReportesDto {

	private ReportesDto() {
	}

	/** Agrupación de los ingresos (RF-21). */
	public enum Periodo {
		DIA, SEMANA, MES
	}

	@Schema(description = "Ingresos de un día, semana o mes (solo pagos PAGADO)")
	public record IngresoPeriodoResponse(
			@Schema(description = "Primer día del periodo (lunes en SEMANA, día 1 en MES)", example = "2026-10-26") LocalDate inicio,
			@Schema(example = "57") long pedidosPagados,
			@Schema(example = "612.50") BigDecimal ingresoTotal,
			@Schema(example = "10.75") BigDecimal ticketPromedio) {
	}

	@Schema(description = "Producto en el ranking de más vendidos (RF-22)")
	public record ProductoVendidoResponse(
			@Schema(example = "1") Integer idProducto,
			@Schema(example = "Menú del día") String nombre,
			@Schema(example = "Menú") String categoria,
			@Schema(example = "120") long unidadesVendidas,
			@Schema(example = "960.00") BigDecimal ingresoGenerado) {
	}

	@Schema(description = "Venta registrada (RF-20): un pago PAGADO")
	public record VentaResponse(
			Integer idPago,
			@Schema(example = "PED-0425") String codigoPedido,
			LocalDateTime fecha,
			@Schema(example = "10.00") BigDecimal monto,
			MetodoPago metodoPago,
			@Schema(example = "Eliú Barrera Martínez") String estudiante) {
	}

}
