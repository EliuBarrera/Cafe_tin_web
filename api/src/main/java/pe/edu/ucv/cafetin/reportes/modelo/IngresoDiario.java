package pe.edu.ucv.cafetin.reportes.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Vista {@code v_ingresos_diarios} (RF-21), solo lectura: pagos PAGADO agrupados por día (hora de Lima).
 * Semana y mes se calculan agrupando pagos con {@code date_trunc}, no desde esta vista.
 */
@Entity
@Immutable
@Table(name = "v_ingresos_diarios")
public class IngresoDiario {

	@Id
	private LocalDate dia;

	@Column(name = "pedidos_pagados")
	private Long pedidosPagados;

	@Column(name = "ingreso_total")
	private BigDecimal ingresoTotal;

	@Column(name = "ticket_promedio")
	private BigDecimal ticketPromedio;

	protected IngresoDiario() {
	}

	public LocalDate getDia() { return dia; }
	public Long getPedidosPagados() { return pedidosPagados; }
	public BigDecimal getIngresoTotal() { return ingresoTotal; }
	public BigDecimal getTicketPromedio() { return ticketPromedio; }

}
