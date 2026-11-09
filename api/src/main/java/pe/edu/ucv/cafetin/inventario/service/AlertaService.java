package pe.edu.ucv.cafetin.inventario.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.inventario.dto.InventarioDto.AlertaResponse;
import pe.edu.ucv.cafetin.inventario.modelo.AlertaStock;
import pe.edu.ucv.cafetin.inventario.modelo.Inventario;
import pe.edu.ucv.cafetin.inventario.modelo.Producto;
import pe.edu.ucv.cafetin.inventario.modelo.Proveedor;
import pe.edu.ucv.cafetin.inventario.repository.AlertaStockRepository;

/**
 * Alertas de stock del dueño (RF-17). Las crea el trigger {@code trg_alerta_stock_minimo}, una por dueño;
 * aquí solo se listan y se marcan como atendidas. Cada dueño ve y marca solo las suyas.
 */
@Service
public class AlertaService {

	private final AlertaStockRepository alertas;

	public AlertaService(AlertaStockRepository alertas) {
		this.alertas = alertas;
	}

	@Transactional(readOnly = true)
	public List<AlertaResponse> listar(Integer idDueno, boolean atendida) {
		return alertas.delUsuario(idDueno, atendida).stream().map(AlertaService::aRespuesta).toList();
	}

	/** Idempotente: marcar una ya atendida la devuelve igual. Una ajena o inexistente responde 404. */
	@Transactional
	public AlertaResponse marcarAtendida(Integer idAlerta, Integer idDueno) {
		AlertaStock alerta = alertas.deUsuario(idAlerta, idDueno)
				.orElseThrow(() -> new ApiException.NoEncontrado("No existe la alerta " + idAlerta));
		alerta.setAtendida(true);
		return aRespuesta(alerta);
	}

	private static AlertaResponse aRespuesta(AlertaStock a) {
		Inventario i = a.getInventario();
		Producto p = i.getProducto();
		Proveedor prov = p.getProveedor();
		return new AlertaResponse(a.getId(), a.getTipo().name(), a.getMensaje(), a.getFecha(),
				a.getNivelDetectado(), i.getCantidadDisponible(), i.getStockMinimo(), a.isAtendida(),
				p.getId(), p.getNombre(),
				prov == null ? null : prov.getNombre(), prov == null ? null : prov.getTelefono());
	}

}
