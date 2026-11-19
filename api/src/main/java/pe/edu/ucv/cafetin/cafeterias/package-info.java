/**
 * Cafeterías: cada cafetín de la plataforma, con su dueño, trabajadores, categorías, productos y pedidos
 * (decisión del 19/11/2026). El estudiante elige el cafetín; el dueño y el trabajador solo ven el suyo.
 * Responsable: Eliú Barrera.
 *
 * <p>Capas: {@code controller} (valida y delega) → {@code service} (lógica de negocio)
 * → {@code repository} (Spring Data JPA), más {@code dto} para entrada y salida.
 * Nunca se exponen entidades JPA en los controllers.
 */
package pe.edu.ucv.cafetin.cafeterias;
