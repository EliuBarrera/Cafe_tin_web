package pe.edu.ucv.cafetin.inventario.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import pe.edu.ucv.cafetin.comun.modelo.Notificacion;

/**
 * Alerta de stock (RF-17), subtipo de {@link Notificacion}. La crea el trigger
 * {@code trg_alerta_stock_minimo} al cruzar el mínimo (STOCK_BAJO) o al agotarse (STOCK_AGOTADO),
 * una por cada dueño. Java no la crea: solo la lista y la marca como atendida.
 */
@Entity
@Table(name = "alerta_stock")
@PrimaryKeyJoinColumn(name = "id_notificacion")
public class AlertaStock extends Notificacion {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_inventario", nullable = false)
	private Inventario inventario;

	@Column(name = "nivel_detectado", nullable = false)
	private int nivelDetectado;

	@Column(nullable = false)
	private boolean atendida = false;

	protected AlertaStock() {
	}

	public Inventario getInventario() { return inventario; }

	public int getNivelDetectado() { return nivelDetectado; }

	public boolean isAtendida() { return atendida; }
	public void setAtendida(boolean atendida) { this.atendida = atendida; }

}
