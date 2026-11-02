package pe.edu.ucv.cafetin.basedatos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import pe.edu.ucv.cafetin.TestcontainersConfiguration;
import pe.edu.ucv.cafetin.catalogo.modelo.CatalogoDisponible;
import pe.edu.ucv.cafetin.comun.modelo.Notificacion;
import pe.edu.ucv.cafetin.comun.modelo.TipoNotificacion;
import pe.edu.ucv.cafetin.cuentas.modelo.Dueno;
import pe.edu.ucv.cafetin.cuentas.modelo.Estudiante;
import pe.edu.ucv.cafetin.cuentas.modelo.TipoUsuario;
import pe.edu.ucv.cafetin.cuentas.modelo.Trabajador;
import pe.edu.ucv.cafetin.cuentas.modelo.Usuario;
import pe.edu.ucv.cafetin.despacho.modelo.PedidoEnCurso;
import pe.edu.ucv.cafetin.inventario.modelo.AlertaReposicion;
import pe.edu.ucv.cafetin.inventario.modelo.AlertaStock;
import pe.edu.ucv.cafetin.inventario.modelo.Inventario;
import pe.edu.ucv.cafetin.inventario.modelo.Producto;
import pe.edu.ucv.cafetin.pedidos.modelo.EstadoPago;
import pe.edu.ucv.cafetin.pedidos.modelo.EstadoPedido;
import pe.edu.ucv.cafetin.pedidos.modelo.MetodoPago;
import pe.edu.ucv.cafetin.pedidos.modelo.Pago;
import pe.edu.ucv.cafetin.pedidos.modelo.Pedido;
import pe.edu.ucv.cafetin.pedidos.modelo.Recibo;
import pe.edu.ucv.cafetin.reportes.modelo.IngresoDiario;
import pe.edu.ucv.cafetin.reportes.modelo.ProductoMasVendido;

/**
 * Las entidades JPA leen y escriben el esquema real (enums nativos, herencia JOINED, columnas que
 * calcula la base y triggers). Usa los datos de prueba de V2; cada test se revierte al terminar.
 * Mismas propiedades que {@link DatosDevTest}: comparten contexto y contenedor.
 */
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/datos-dev")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class EntidadesTest {

	@Autowired
	EntityManager em;

	// --------------------------------------------------------------- lectura

	@Test
	void usuariosSeCarganComoSuSubtipo() {
		assertThat(em.find(Usuario.class, 1)).isInstanceOf(Dueno.class);
		assertThat(em.find(Usuario.class, 2)).isInstanceOf(Trabajador.class);

		Usuario eliu = em.find(Usuario.class, 4);
		assertThat(eliu).isInstanceOf(Estudiante.class);
		assertThat(eliu.getTipoUsuario()).isEqualTo(TipoUsuario.ESTUDIANTE);
		assertThat(((Estudiante) eliu).getCodigo()).isEqualTo("2021045678");
		assertThat(eliu.getFechaRegistro()).isNotNull();

		Long trabajadores = em.createQuery("SELECT count(t) FROM Trabajador t", Long.class).getSingleResult();
		assertThat(trabajadores).isEqualTo(2);
	}

	@Test
	void pedidoDelSeedConDetallesPagoYRecibo() {
		Pedido pedido = em.createQuery("SELECT p FROM Pedido p WHERE p.codigoPedido = 'PED-0425'", Pedido.class)
				.getSingleResult();
		Pago pago = em.createQuery("SELECT pg FROM Pago pg WHERE pg.pedido = :p", Pago.class)
				.setParameter("p", pedido).getSingleResult();
		Recibo recibo = em.createQuery("SELECT r FROM Recibo r WHERE r.pago = :pg", Recibo.class)
				.setParameter("pg", pago).getSingleResult();

		assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.ENTREGADO);
		assertThat(pedido.getTotal()).isEqualByComparingTo("10.00");
		assertThat(pedido.getDetalles()).hasSize(2)
				.extracting(d -> d.getSubtotal().toPlainString()).containsExactlyInAnyOrder("8.00", "2.00");
		assertThat(pago.getEstado()).isEqualTo(EstadoPago.PAGADO);
		assertThat(pago.getMetodoPago()).isEqualTo(MetodoPago.YAPE);
		assertThat(recibo.getCodigoVerificacion()).isEqualTo("DR-0425-7781");
		assertThat(recibo.isVerificado()).isTrue();
	}

	@Test
	void lasCincoVistasSeLeenComoEntidades() {
		var catalogo = em.createQuery("SELECT c FROM CatalogoDisponible c", CatalogoDisponible.class).getResultList();
		var despacho = em.createQuery(
				"SELECT p FROM PedidoEnCurso p WHERE p.estadoPago = :pagado", PedidoEnCurso.class)
				.setParameter("pagado", EstadoPago.PAGADO).getResultList();
		var ingresos = em.createQuery("SELECT i FROM IngresoDiario i", IngresoDiario.class).getResultList();
		var masVendidos = em.createQuery(
				"SELECT m FROM ProductoMasVendido m ORDER BY m.unidadesVendidas DESC, m.nombre", ProductoMasVendido.class)
				.getResultList();
		var reposicion = em.createQuery("SELECT a FROM AlertaReposicion a", AlertaReposicion.class).getResultList();

		assertThat(catalogo).hasSize(9);
		assertThat(despacho).extracting(PedidoEnCurso::getCodigoPedido).containsExactly("PED-0426");
		assertThat(despacho.get(0).getEstado()).isEqualTo(EstadoPedido.EN_PREPARACION);
		assertThat(ingresos).hasSize(4);
		assertThat(masVendidos.get(0).getUnidadesVendidas()).isEqualTo(3L);
		assertThat(reposicion).extracting(AlertaReposicion::getProducto)
				.contains("Tamal criollo", "Empanada de ají", "Jugo de piña 500 ml");
	}

	// -------------------------------------------------------------- escritura

	@Test
	void pedidoCreadoConJpaRecibeTotalDelTrigger() {
		Estudiante nueva = new Estudiante("Lucía", "Prueba Torres", "lucia@ucvvirtual.edu.pe", "$2a$10$hash",
				"2023000001", "Psicología", (short) 2);
		em.persist(nueva); // el discriminador tipo_usuario (enum) lo escribe Hibernate

		Producto cafe = em.find(Producto.class, 7);   // 2.50
		Producto jugo = em.find(Producto.class, 6);   // 4.50
		Pedido pedido = new Pedido("PED-9001", nueva, LocalTime.of(11, 30));
		pedido.agregarDetalle(cafe, 2);
		pedido.agregarDetalle(jugo, 1);
		em.persist(pedido);
		em.flush();
		em.refresh(pedido); // total lo escribe trg_calcular_total_pedido

		assertThat(pedido.getTotal()).isEqualByComparingTo("9.50");
		assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.PENDIENTE);
		assertThat(pedido.getFechaHora()).isNotNull();
		assertThat(pedido.getDetalles()).extracting(d -> d.getSubtotal().toPlainString())
				.containsExactlyInAnyOrder("5.00", "4.50");
		assertThat(em.find(Usuario.class, nueva.getId())).isInstanceOf(Estudiante.class);
	}

	@Test
	void pagoPendienteQuePasaAPagadoDescuentaStock() {
		Estudiante diego = em.find(Estudiante.class, 6);
		Producto galletas = em.find(Producto.class, 8);
		Inventario inv = em.createQuery("SELECT i FROM Inventario i WHERE i.producto = :p", Inventario.class)
				.setParameter("p", galletas).getSingleResult();
		int antes = inv.getCantidadDisponible();

		Pedido pedido = new Pedido("PED-9002", diego, LocalTime.of(15, 0));
		pedido.agregarDetalle(galletas, 3);
		em.persist(pedido);
		em.flush();
		em.refresh(pedido);

		Pago pago = new Pago(pedido, pedido.getTotal(), MetodoPago.PLIN);
		em.persist(pago);
		em.flush();
		em.refresh(inv);
		assertThat(inv.getCantidadDisponible()).isEqualTo(antes);   // PENDIENTE no descuenta

		pago.setEstado(EstadoPago.PAGADO);
		em.flush();
		em.refresh(inv);                                            // el trigger cambió la fila
		assertThat(inv.getCantidadDisponible()).isEqualTo(antes - 3);

		Recibo recibo = new Recibo(pago, "DR-9002-0001");
		em.persist(recibo);
		em.flush();
		assertThat(recibo.getFechaEmision()).isNotNull();
		assertThat(pago.getMonto()).isEqualByComparingTo("7.50");
	}

	@Test
	void alertaDelTriggerSeLeeComoSubtipoDeNotificacion() {
		Inventario pan = em.find(Inventario.class, 3);   // 12 unidades, mínimo 5
		pan.setCantidadDisponible(5);                    // cruza el mínimo
		em.flush();

		var alertas = em.createQuery("SELECT a FROM AlertaStock a", AlertaStock.class).getResultList();
		assertThat(alertas).hasSize(1);
		AlertaStock alerta = alertas.get(0);
		assertThat(alerta.getTipo()).isEqualTo(TipoNotificacion.STOCK_BAJO);
		assertThat(alerta.getNivelDetectado()).isEqualTo(5);
		assertThat(alerta.getInventario().getId()).isEqualTo(3);
		// Relación LAZY a Usuario = proxy de la clase base: instanceof Dueno daría false.
		assertThat(alerta.getUsuario().getTipoUsuario()).isEqualTo(TipoUsuario.DUENO);

		// Consulta polimórfica: las 2 notificaciones del seed + la alerta
		var todas = em.createQuery("SELECT n FROM Notificacion n", Notificacion.class).getResultList();
		assertThat(todas).hasSize(3).filteredOn(n -> n instanceof AlertaStock).hasSize(1);
	}

	@Test
	void productoNuevoConSuInventario() {
		Producto producto = new Producto(em.find(pe.edu.ucv.cafetin.inventario.modelo.Categoria.class, 3),
				"Chifles 50 g", new BigDecimal("2.00"));
		em.persist(producto);
		Inventario inventario = new Inventario(producto, 15, 5);
		em.persist(inventario);
		em.flush();

		assertThat(producto.getFechaRegistro()).isNotNull();
		assertThat(inventario.getFechaActualizacion()).isNotNull();
		CatalogoDisponible enCatalogo = em.find(CatalogoDisponible.class, producto.getId());
		assertThat(enCatalogo.getEstadoStock()).isEqualTo("DISPONIBLE");
	}

}
