package pe.edu.ucv.cafetin.reportes.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import pe.edu.ucv.cafetin.pedidos.modelo.MetodoPago;
import pe.edu.ucv.cafetin.reportes.dto.ReportesDto.IngresoPeriodoResponse;
import pe.edu.ucv.cafetin.reportes.dto.ReportesDto.VentaResponse;

/**
 * Consultas de ventas (RF-20, RF-21) sobre {@code pago}, en SQL: agrupar por semana o mes necesita
 * {@code date_trunc}, que la vista {@code v_ingresos_diarios} no ofrece. Una venta = un pago PAGADO,
 * fechado con {@code pago.fecha} (hora de Lima). El rango va de {@code desde} a {@code hasta}, ambos incluidos.
 */
@Repository
public class VentaRepository {

	private static final String RANGO = """
			pg.estado = 'PAGADO' AND pg.fecha >= :desde AND pg.fecha < CAST(:hasta AS date) + 1""";

	private final NamedParameterJdbcTemplate jdbc;

	public VentaRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Fecha de hoy en la base (Lima), no la del servidor de la API, que en Render está en UTC. */
	public LocalDate hoy() {
		return jdbc.getJdbcTemplate().queryForObject("SELECT CURRENT_DATE", LocalDate.class);
	}

	/** @param unidad {@code day}, {@code week} (empieza en lunes) o {@code month}, como en {@code date_trunc}. */
	public List<IngresoPeriodoResponse> ingresos(String unidad, LocalDate desde, LocalDate hasta) {
		return jdbc.query("""
				SELECT CAST(date_trunc(:unidad, pg.fecha) AS date) AS inicio,
				       count(*)                AS pedidos_pagados,
				       sum(pg.monto)           AS ingreso_total,
				       round(avg(pg.monto), 2) AS ticket_promedio
				FROM pago pg
				WHERE\s""" + RANGO + """

				GROUP BY 1
				ORDER BY 1""",
				Map.of("unidad", unidad, "desde", desde, "hasta", hasta),
				(rs, i) -> new IngresoPeriodoResponse(rs.getObject("inicio", LocalDate.class),
						rs.getLong("pedidos_pagados"), rs.getBigDecimal("ingreso_total"), rs.getBigDecimal("ticket_promedio")));
	}

	public long contarVentas(LocalDate desde, LocalDate hasta) {
		return jdbc.queryForObject("SELECT count(*) FROM pago pg WHERE " + RANGO,
				Map.of("desde", desde, "hasta", hasta), Long.class);
	}

	public List<VentaResponse> ventas(LocalDate desde, LocalDate hasta, int pagina, int tamano) {
		return jdbc.query("""
				SELECT pg.id_pago, pe.codigo_pedido, pg.fecha, pg.monto, pg.metodo_pago,
				       u.nombre || ' ' || u.apellidos AS estudiante
				FROM pago pg
				JOIN pedido  pe ON pe.id_pedido  = pg.id_pedido
				JOIN usuario u  ON u.id_usuario  = pe.id_estudiante
				WHERE\s""" + RANGO + """

				ORDER BY pg.fecha DESC, pg.id_pago DESC
				LIMIT :tamano OFFSET :salto""",
				Map.of("desde", desde, "hasta", hasta, "tamano", tamano, "salto", (long) pagina * tamano),
				(rs, i) -> new VentaResponse(rs.getInt("id_pago"), rs.getString("codigo_pedido"),
						rs.getTimestamp("fecha").toLocalDateTime(), rs.getBigDecimal("monto"),
						MetodoPago.valueOf(rs.getString("metodo_pago")), rs.getString("estudiante")));
	}

}
