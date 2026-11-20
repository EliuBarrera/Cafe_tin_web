package pe.edu.ucv.cafetin.pedidos.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

import pe.edu.ucv.cafetin.pedidos.modelo.Pago;
import pe.edu.ucv.cafetin.pedidos.modelo.Pedido;
import pe.edu.ucv.cafetin.pedidos.modelo.Recibo;
import pe.edu.ucv.cafetin.pedidos.repository.ReciboRepository;

/**
 * Recibo digital de un pago PAGADO (RF-07). Lo usan la pasarela simulada ({@link PedidoService}) y la
 * confirmación de Mercado Pago, para que ambos emitan el mismo formato de código.
 */
@Component
public class EmisorRecibos {

	private final ReciboRepository recibos;
	private final SecureRandom azar = new SecureRandom();

	public EmisorRecibos(ReciboRepository recibos) {
		this.recibos = recibos;
	}

	public Recibo emitir(Pago pago, Pedido pedido) {
		return recibos.saveAndFlush(new Recibo(pago, nuevoCodigo(pedido)));
	}

	/** {@code DR-<nº del pedido>-<4 dígitos>}; se reintenta si el código ya existe (UNIQUE). */
	private String nuevoCodigo(Pedido pedido) {
		String numero = pedido.getCodigoPedido().substring("PED-".length());
		for (int intento = 0; intento < 10; intento++) {
			String codigo = "DR-%s-%04d".formatted(numero, azar.nextInt(10_000));
			if (!recibos.existsByCodigoVerificacion(codigo)) {
				return codigo;
			}
		}
		throw new IllegalStateException("No se pudo generar un código de recibo único para " + pedido.getCodigoPedido());
	}

}
