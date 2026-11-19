/**
 * Cobro con Mercado Pago (RF-06, decisión del 19/11/2026): cada cafetín conecta su cuenta por OAuth
 * y el estudiante paga en Checkout Pro; el dinero llega a la cuenta del cafetín (marketplace, con
 * comisión configurable). Solo se activa con {@code cafetin.pagos.pasarela=mercadopago}; si no, se
 * usa la pasarela simulada. Responsable: Eliú Barrera.
 *
 * <p>Capas: {@code controller} (valida y delega) → {@code service} (lógica de negocio)
 * → {@code repository}, más {@code dto} para entrada y salida.
 */
package pe.edu.ucv.cafetin.mercadopago;
