/**
 * Paquete 1 — Cuentas y acceso (RF-01, RF-02, RF-03): registro, login JWT, roles y recuperación de contraseña. Responsable: José Daniel Lizca.
 *
 * <p>Capas: {@code controller} (valida y delega) → {@code service} (lógica de negocio)
 * → {@code repository} (Spring Data JPA), más {@code dto} para entrada y salida.
 * Nunca se exponen entidades JPA en los controllers.
 */
package pe.edu.ucv.cafetin.cuentas;
