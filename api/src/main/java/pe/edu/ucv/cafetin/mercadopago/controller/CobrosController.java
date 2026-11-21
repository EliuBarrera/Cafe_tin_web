package pe.edu.ucv.cafetin.mercadopago.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import pe.edu.ucv.cafetin.comun.error.ErrorRespuesta;
import pe.edu.ucv.cafetin.cuentas.seguridad.UsuarioActual;
import pe.edu.ucv.cafetin.mercadopago.dto.MercadoPagoDto.EstadoConexionResponse;
import pe.edu.ucv.cafetin.mercadopago.dto.MercadoPagoDto.UrlAutorizacionResponse;
import pe.edu.ucv.cafetin.mercadopago.service.ConexionMercadoPagoService;

/** Cuenta de Mercado Pago del cafetín del dueño autenticado (página "Cobros" del panel). */
@RestController
@RequestMapping("/api/cafeterias/mia/mercadopago")
@Tag(name = "Cobros")
public class CobrosController {

	private final ConexionMercadoPagoService servicio;
	private final UsuarioActual usuario;

	public CobrosController(ConexionMercadoPagoService servicio, UsuarioActual usuario) {
		this.servicio = servicio;
		this.usuario = usuario;
	}

	@GetMapping
	@Operation(summary = "Estado de la cuenta de Mercado Pago", description = """
			DUENO · RF-06. Si el cafetín conectó su cuenta y con qué pasarela cobra la API. Nunca devuelve tokens.""")
	public EstadoConexionResponse estado() {
		return servicio.estado(usuario.cafeteria());
	}

	@PostMapping("/conectar")
	@Operation(summary = "Conectar Mercado Pago", description = """
			DUENO · RF-06. Devuelve la página de Mercado Pago donde el dueño autoriza al cafetín a cobrar.
			El panel navega a esa URL; Mercado Pago vuelve a /api/mercadopago/oauth/retorno y de ahí al
			panel con ?mercadopago=conectado o ?mercadopago=error. El enlace vale 10 minutos.""")
	@ApiResponse(responseCode = "409", description = "La API cobra con la pasarela simulada (REGLA_NEGOCIO)",
			content = @Content(schema = @Schema(implementation = ErrorRespuesta.class)))
	@ResponseStatus(HttpStatus.OK)
	public UrlAutorizacionResponse conectar() {
		return servicio.iniciar(usuario.cafeteria());
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Desconectar Mercado Pago", description = """
			DUENO · RF-06. Borra los tokens del cafetín: deja de aceptar pagos hasta que vuelva a conectar.""")
	public void desconectar() {
		servicio.desconectar(usuario.cafeteria());
	}

}
