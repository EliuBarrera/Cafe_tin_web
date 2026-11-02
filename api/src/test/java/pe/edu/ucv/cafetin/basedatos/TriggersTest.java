package pe.edu.ucv.cafetin.basedatos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ucv.cafetin.TestcontainersConfiguration;

/**
 * Pruebas de humo de la lógica que vive en la base (CLAUDE.md, "Lo que hace la BD").
 * Usa SQL directo porque las entidades JPA llegan en el siguiente PR.
 * Cada test corre en una transacción que se revierte al terminar.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class TriggersTest {

	@Autowired
	JdbcTemplate jdbc;

	// ----------------------------------------------------------------- datos

	private int estudiante() {
		int id = jdbc.queryForObject("""
				INSERT INTO cafetin.usuario (nombre, apellidos, correo, contrasena, tipo_usuario)
				VALUES ('Ana', 'Prueba', 'ana@ucvvirtual.edu.pe', 'x', 'ESTUDIANTE') RETURNING id_usuario""",
				Integer.class);
		jdbc.update("INSERT INTO cafetin.estudiante (id_usuario, codigo, carrera, ciclo) VALUES (?, 'T001', 'Sistemas', 3)", id);
		return id;
	}

	private void dueno() {
		int id = jdbc.queryForObject("""
				INSERT INTO cafetin.usuario (nombre, apellidos, correo, contrasena, tipo_usuario)
				VALUES ('Rosa', 'Prueba', 'rosa@cafetin.pe', 'x', 'DUENO') RETURNING id_usuario""",
				Integer.class);
		jdbc.update("INSERT INTO cafetin.dueno (id_usuario) VALUES (?)", id);
	}

	private int categoria() {
		return jdbc.queryForObject(
				"INSERT INTO cafetin.categoria (nombre) VALUES ('Bebidas') RETURNING id_categoria", Integer.class);
	}

	private int producto(int categoria, String nombre, String precio, int stock, int minimo) {
		int id = jdbc.queryForObject("""
				INSERT INTO cafetin.producto (id_categoria, nombre, precio) VALUES (?, ?, ?::numeric)
				RETURNING id_producto""", Integer.class, categoria, nombre, precio);
		jdbc.update("""
				INSERT INTO cafetin.inventario (id_producto, cantidad_disponible, stock_minimo)
				VALUES (?, ?, ?)""", id, stock, minimo);
		return id;
	}

	private int pedido(int estudiante) {
		return jdbc.queryForObject("""
				INSERT INTO cafetin.pedido (codigo_pedido, id_estudiante, hora_recojo)
				VALUES ('PED-' || nextval('cafetin.pedido_id_pedido_seq'), ?, '12:00') RETURNING id_pedido""",
				Integer.class, estudiante);
	}

	private void detalle(int pedido, int producto, int cantidad, String precio) {
		jdbc.update("""
				INSERT INTO cafetin.detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario)
				VALUES (?, ?, ?, ?::numeric)""", pedido, producto, cantidad, precio);
	}

	private void pagoPendiente(int pedido) {
		jdbc.update("""
				INSERT INTO cafetin.pago (id_pedido, monto, metodo_pago)
				SELECT id_pedido, total, 'YAPE' FROM cafetin.pedido WHERE id_pedido = ?""", pedido);
	}

	private void pagar(int pedido) {
		jdbc.update("UPDATE cafetin.pago SET estado = 'PAGADO' WHERE id_pedido = ?", pedido);
	}

	private int stock(int producto) {
		return jdbc.queryForObject(
				"SELECT cantidad_disponible FROM cafetin.inventario WHERE id_producto = ?", Integer.class, producto);
	}

	private BigDecimal total(int pedido) {
		return jdbc.queryForObject("SELECT total FROM cafetin.pedido WHERE id_pedido = ?", BigDecimal.class, pedido);
	}

	// ----------------------------------------------------------------- tests

	@Test
	void totalDelPedidoLoCalculaElTrigger() {
		int cat = categoria();
		int cafe = producto(cat, "Café", "2.50", 20, 5);
		int jugo = producto(cat, "Jugo", "4.50", 20, 5);
		int pedido = pedido(estudiante());

		detalle(pedido, cafe, 2, "2.50");
		detalle(pedido, jugo, 1, "4.50");

		assertThat(total(pedido)).isEqualByComparingTo("9.50");
	}

	@Test
	void pagoActualizadoAPagadoDescuentaStock() {
		int cat = categoria();
		int cafe = producto(cat, "Café", "2.50", 20, 5);
		int pedido = pedido(estudiante());
		detalle(pedido, cafe, 3, "2.50");
		pagoPendiente(pedido);

		assertThat(stock(cafe)).isEqualTo(20);   // PENDIENTE todavía no descuenta
		pagar(pedido);
		assertThat(stock(cafe)).isEqualTo(17);
	}

	@Test
	void pagoInsertadoYaPagadoNoDescuentaStock() {
		// Por eso la API siempre inserta PENDIENTE y luego actualiza a PAGADO.
		int cat = categoria();
		int cafe = producto(cat, "Café", "2.50", 20, 5);
		int pedido = pedido(estudiante());
		detalle(pedido, cafe, 3, "2.50");

		jdbc.update("""
				INSERT INTO cafetin.pago (id_pedido, monto, metodo_pago, estado)
				SELECT id_pedido, total, 'YAPE', 'PAGADO' FROM cafetin.pedido WHERE id_pedido = ?""", pedido);

		assertThat(stock(cafe)).isEqualTo(20);
	}

	@Test
	void pagarSinStockSuficienteFallaPorCheck() {
		int cat = categoria();
		int cafe = producto(cat, "Café", "2.50", 2, 1);
		int pedido = pedido(estudiante());
		detalle(pedido, cafe, 3, "2.50");
		pagoPendiente(pedido);

		assertThatThrownBy(() -> pagar(pedido)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void alertaSoloAlCruzarElMinimoYAlAgotarse() {
		dueno();
		int cat = categoria();
		int cafe = producto(cat, "Café", "2.50", 12, 10);

		jdbc.update("UPDATE cafetin.inventario SET cantidad_disponible = 10 WHERE id_producto = ?", cafe); // cruza
		jdbc.update("UPDATE cafetin.inventario SET cantidad_disponible = 9  WHERE id_producto = ?", cafe); // sigue bajo
		jdbc.update("UPDATE cafetin.inventario SET cantidad_disponible = 0  WHERE id_producto = ?", cafe); // se agota

		var tipos = jdbc.queryForList("""
				SELECT n.tipo::text FROM cafetin.alerta_stock a
				JOIN cafetin.notificacion n USING (id_notificacion) ORDER BY n.id_notificacion""", String.class);
		assertThat(tipos).containsExactly("STOCK_BAJO", "STOCK_AGOTADO");

		Boolean activo = jdbc.queryForObject("SELECT activo FROM cafetin.producto WHERE id_producto = ?",
				Boolean.class, cafe);
		String estado = jdbc.queryForObject(
				"SELECT estado_stock FROM cafetin.v_catalogo_disponible WHERE id_producto = ?", String.class, cafe);
		assertThat(activo).isTrue();
		assertThat(estado).isEqualTo("AGOTADO");
	}

}
