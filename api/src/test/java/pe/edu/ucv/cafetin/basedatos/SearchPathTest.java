package pe.edu.ucv.cafetin.basedatos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.zaxxer.hikari.HikariDataSource;

import pe.edu.ucv.cafetin.TestcontainersConfiguration;

/**
 * Cada conexión debe quedar con {@code search_path = cafetin}: las funciones de los triggers nombran
 * tablas y tipos sin esquema. Neon ignora el parámetro {@code currentSchema} del driver (la conexión
 * quedaba en {@code "$user", public} y fallaban crear pedido, pagar y editar inventario), pero sí
 * respeta {@code options=-c search_path=cafetin}. Testcontainers respeta los dos, así que además de
 * comprobar el search_path se fija que la configuración use {@code options}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SearchPathTest {

	@Autowired
	HikariDataSource fuente;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void cadaConexionUsaElEsquemaCafetin() {
		assertThat(jdbc.queryForObject("SHOW search_path", String.class)).isEqualTo("cafetin");
	}

	@Test
	void elSearchPathSeFijaConOptionsYNoConCurrentSchema() {
		assertThat(fuente.getDataSourceProperties())
				.containsEntry("options", "-c search_path=cafetin")
				.doesNotContainKey("currentSchema");
	}

}
