package pe.edu.ucv.cafetin.basedatos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import pe.edu.ucv.cafetin.TestcontainersConfiguration;

/** Flyway aplica V1 sobre PostgreSQL 16 y deja el esquema completo (perfil test: sin V2). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class MigracionesTest {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void flywayAplicaSoloV1EnPerfilTest() {
		var versiones = jdbc.queryForList(
				// version NULL = fila con la que Flyway registra que creó el esquema
				"SELECT version FROM cafetin.flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank",
				String.class);
		assertThat(versiones).containsExactly("1");
	}

	@Test
	void esquemaTiene14TablasY5Vistas() {
		Integer tablas = jdbc.queryForObject("""
				SELECT count(*) FROM information_schema.tables
				WHERE table_schema = 'cafetin' AND table_type = 'BASE TABLE'
				  AND table_name <> 'flyway_schema_history'""", Integer.class);
		var vistas = jdbc.queryForList("""
				SELECT table_name FROM information_schema.views
				WHERE table_schema = 'cafetin' ORDER BY table_name""", String.class);

		assertThat(tablas).isEqualTo(14);
		assertThat(vistas).containsExactly("v_alertas_reposicion", "v_catalogo_disponible",
				"v_ingresos_diarios", "v_pedidos_en_curso", "v_productos_mas_vendidos");
	}

	@Test
	void conexionesUsanHoraDeLima() {
		assertThat(jdbc.queryForObject("SHOW timezone", String.class)).isEqualTo("America/Lima");
	}

	@Test
	void conexionesUsanEsquemaCafetin() {
		// Sin esto, los triggers (que nombran tablas sin esquema) fallan desde la API.
		assertThat(jdbc.queryForObject("SELECT current_schema()", String.class)).isEqualTo("cafetin");
	}

	@Test
	void usuarioTieneColumnaFcmToken() {
		Integer columnas = jdbc.queryForObject("""
				SELECT count(*) FROM information_schema.columns
				WHERE table_schema = 'cafetin' AND table_name = 'usuario' AND column_name = 'fcm_token'""",
				Integer.class);
		assertThat(columnas).isEqualTo(1);
	}

}
