package pe.edu.ucv.cafetin.reportes.service;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ucv.cafetin.comun.dto.Pagina;
import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.comun.error.CodigoError;
import pe.edu.ucv.cafetin.reportes.dto.ReportesDto.IngresoPeriodoResponse;
import pe.edu.ucv.cafetin.reportes.dto.ReportesDto.Periodo;
import pe.edu.ucv.cafetin.reportes.dto.ReportesDto.ProductoVendidoResponse;
import pe.edu.ucv.cafetin.reportes.dto.ReportesDto.VentaResponse;
import pe.edu.ucv.cafetin.reportes.repository.ProductoMasVendidoRepository;
import pe.edu.ucv.cafetin.reportes.repository.VentaRepository;

/**
 * Ingresos del dueño: ventas (RF-20), ingresos por periodo (RF-21) y más vendidos (RF-22).
 * Solo cuentan los pagos PAGADO. Sin fechas, el rango son los últimos 30 días contados en la base.
 */
@Service
@Transactional(readOnly = true)
public class ReporteService {

	private static final int DIAS_POR_DEFECTO = 30;

	private final VentaRepository ventas;
	private final ProductoMasVendidoRepository masVendidos;

	public ReporteService(VentaRepository ventas, ProductoMasVendidoRepository masVendidos) {
		this.ventas = ventas;
		this.masVendidos = masVendidos;
	}

	/** RF-21. Ordenados del periodo más antiguo al más reciente; los periodos sin ventas no aparecen. */
	public List<IngresoPeriodoResponse> ingresos(Periodo periodo, LocalDate desde, LocalDate hasta) {
		Rango r = rango(desde, hasta);
		String unidad = switch (periodo) {
			case DIA -> "day";
			case SEMANA -> "week";
			case MES -> "month";
		};
		return ventas.ingresos(unidad, r.desde(), r.hasta());
	}

	/** RF-22. Todo el histórico. */
	public List<ProductoVendidoResponse> masVendidos(int limite) {
		return masVendidos.findAllByOrderByUnidadesVendidasDescIngresoGeneradoDescIdProductoAsc(Limit.of(limite))
				.stream()
				.map(p -> new ProductoVendidoResponse(p.getIdProducto(), p.getNombre(), p.getCategoria(),
						p.getUnidadesVendidas(), p.getIngresoGenerado()))
				.toList();
	}

	/** RF-20. Más recientes primero. */
	public Pagina<VentaResponse> ventas(LocalDate desde, LocalDate hasta, int pagina, int tamano) {
		Rango r = rango(desde, hasta);
		long total = ventas.contarVentas(r.desde(), r.hasta());
		List<VentaResponse> contenido = ventas.ventas(r.desde(), r.hasta(), pagina, tamano);
		return Pagina.de(new PageImpl<>(contenido, PageRequest.of(pagina, tamano), total), Function.identity());
	}

	/** Sin {@code hasta}: hoy. Sin {@code desde}: 30 días hasta {@code hasta}, incluidos ambos extremos. */
	private Rango rango(LocalDate desde, LocalDate hasta) {
		LocalDate fin = hasta != null ? hasta : ventas.hoy();
		LocalDate inicio = desde != null ? desde : fin.minusDays(DIAS_POR_DEFECTO - 1);
		if (inicio.isAfter(fin)) {
			throw new ApiException(CodigoError.PARAMETRO_INVALIDO, "'desde' no puede ser posterior a 'hasta'");
		}
		return new Rango(inicio, fin);
	}

	private record Rango(LocalDate desde, LocalDate hasta) {
	}

}
