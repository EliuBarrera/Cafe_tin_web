/**
 * Paquete 3 — Despacho (RF-10 a RF-14): pedidos en curso, máquina de estados, verificación del recibo y entrega. Responsable: Eliú Barrera.
 *
 * <p>Capas: {@code controller} (valida y delega) → {@code service} (lógica de negocio)
 * → {@code repository} (Spring Data JPA), más {@code dto} para entrada y salida.
 * Nunca se exponen entidades JPA en los controllers.
 */
package pe.edu.ucv.cafetin.despacho;
