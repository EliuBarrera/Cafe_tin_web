/**
 * Paquete 2 — Pedidos y pagos (RF-05 a RF-09, RF-16, RF-20): carrito, pago con PasarelaPago, recibo e historial. Responsable: Eliú Barrera.
 *
 * <p>Capas: {@code controller} (valida y delega) → {@code service} (lógica de negocio)
 * → {@code repository} (Spring Data JPA), más {@code dto} para entrada y salida.
 * Nunca se exponen entidades JPA en los controllers.
 */
package pe.edu.ucv.cafetin.pedidos;
