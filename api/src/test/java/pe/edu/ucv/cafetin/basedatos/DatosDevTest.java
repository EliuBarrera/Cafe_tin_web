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
 * Verifica que V2 (datos de prueba del perfil dev) se aplica y deja datos útiles.
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

		assertThat(hashes).hasSize(6).allSatisfy(h -> assertThat(bcrypt.matches("Cafetin2026", h)).isTrue());
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
	void despachoVeElPedidoPagadoEnPreparacion() {
		var pagadosEnCurso = jdbc.queryForList("""
				SELECT codigo_pedido FROM cafetin.v_pedidos_en_curso WHERE estado_pago = 'PAGADO'""", String.class);
		assertThat(pagadosEnCurso).containsExactly("PED-0426");
	}

}
