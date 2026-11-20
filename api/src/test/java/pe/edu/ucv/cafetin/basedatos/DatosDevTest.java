package pe.edu.ucv.cafetin.basedatos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import pe.edu.ucv.cafetin.TestcontainersConfiguration;

/**
 * Verifica que V2 y V12 (datos de prueba del perfil dev) se aplican y dejan datos útiles.
 * Usa su propio contenedor porque agrega db/datos-dev a las ubicaciones de Flyway.
 */
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/datos-dev")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class DatosDevTest {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void contrasenasDelSeedSonBcryptValidos() {
		var hashes = jdbc.queryForList("SELECT contrasena FROM cafetin.usuario", String.class);
		var bcrypt = new BCryptPasswordEncoder();

		assertThat(hashes).hasSize(8).allSatisfy(h -> assertThat(bcrypt.matches("Cafetin2026", h)).isTrue());
	}

	@Test
	void pagosDelSeedDescontaronStockYAlimentanReportes() {
		// Menú del día: 40 iniciales - 1 (PED-0425) - 2 (PED-0423)
		Integer menu = jdbc.queryForObject(
				"SELECT cantidad_disponible FROM cafetin.inventario WHERE id_producto = 1", Integer.class);
		Integer dias = jdbc.queryForObject("SELECT count(*) FROM cafetin.v_ingresos_diarios", Integer.class);
		Integer montosDistintos = jdbc.queryForObject("""
				SELECT count(*) FROM cafetin.pago pg JOIN cafetin.pedido pe USING (id_pedido)
				WHERE pg.monto <> pe.total""", Integer.class);

		assertThat(menu).isEqualTo(37);
		assertThat(dias).isEqualTo(4);
		assertThat(montosDistintos).isZero();
	}

	@Test
	void seedTieneDosCafeteriasCadaUnaConSuDuenoYProductos() {
		var filas = jdbc.queryForList("""
				SELECT c.nombre,
				       (SELECT count(*) FROM cafetin.dueno d WHERE d.id_cafeteria = c.id_cafeteria)    AS duenos,
				       (SELECT count(*) FROM cafetin.producto p WHERE p.id_cafeteria = c.id_cafeteria) AS productos
				FROM cafetin.cafeteria c ORDER BY c.id_cafeteria""");

		assertThat(filas).extracting(f -> f.get("nombre")).containsExactly("Cafetín principal", "Cafetín Pabellón B");
		assertThat(filas).extracting(f -> ((Number) f.get("duenos")).intValue()).containsExactly(1, 1);
		assertThat(filas).extracting(f -> ((Number) f.get("productos")).intValue()).containsExactly(9, 4);
	}

	@Test
	void despachoVeElPedidoPagadoEnPreparacion() {
		var pagadosEnCurso = jdbc.queryForList("""
				SELECT codigo_pedido FROM cafetin.v_pedidos_en_curso WHERE estado_pago = 'PAGADO'""", String.class);
		assertThat(pagadosEnCurso).containsExactly("PED-0426");
	}

}
