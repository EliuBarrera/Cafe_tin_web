package pe.edu.ucv.cafetin.cuentas.service;

/** Entrega al usuario el token de recuperación (RF-03). */
public interface EnvioRecuperacion {

	void enviar(String correo, String token);

}
