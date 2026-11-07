package pe.edu.ucv.cafetin.soporte;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

/**
 * Datos de prueba propios de cada test (el perfil {@code test} no carga el seed V2). Inserta por SQL
 * para no depender de repositorios de otros módulos. Los nombres llevan un sufijo único porque
 * todos los tests comparten el mismo contenedor.
 */
public class DatosPrueba {

	private static final AtomicInteger SECUENCIA = new AtomicInteger(5000);

	private final JdbcTemplate jdbc;

	public DatosPrueba(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public static int siguiente() {
		return SECUENCIA.incrementAndGet();
	}

	public int estudiante() {
		int n = siguiente();
		Integer id = jdbc.queryForObject("""
				INSERT INTO usuario (nombre, apellidos, correo, contrasena, tipo_usuario)
				VALUES ('Ana', 'Prueba', ?, 'x', 'ESTUDIANTE') RETURNING id_usuario""",
				Integer.class, "est" + n + "@ucvvirtual.edu.pe");
		jdbc.update("INSERT INTO estudiante (id_usuario, codigo, carrera, ciclo) VALUES (?, ?, 'Sistemas', 6)",
				id, "E" + n);
		return id;
	}

	public int categoria() {
		return jdbc.queryForObject("INSERT INTO categoria (nombre) VALUES (?) RETURNING id_categoria",
				Integer.class, "Cat " + siguiente());
	}

	/** Producto activo con su inventario (stock mínimo 5). */
	public int producto(int idCategoria, String precio, int stock) {
		Integer id = jdbc.queryForObject("""
				INSERT INTO producto (id_categoria, nombre, descripcion, precio)
				VALUES (?, ?, 'De prueba', ?) RETURNING id_producto""",
				Integer.class, idCategoria, "Producto " + siguiente(), new BigDecimal(precio));
		jdbc.update("INSERT INTO inventario (id_producto, cantidad_disponible, stock_minimo) VALUES (?, ?, 5)",
				id, stock);
		return id;
	}

	public void desactivar(int idProducto) {
		jdbc.update("UPDATE producto SET activo = FALSE WHERE id_producto = ?", idProducto);
	}

	public void cambiarPrecio(int idProducto, String precio) {
		jdbc.update("UPDATE producto SET precio = ? WHERE id_producto = ?", new BigDecimal(precio), idProducto);
	}

	public int stock(int idProducto) {
		return jdbc.queryForObject("SELECT cantidad_disponible FROM inventario WHERE id_producto = ?",
				Integer.class, idProducto);
	}

	/** Usuario autenticado sin JWT: {@code UsuarioActual.id()} lee el id del nombre. */
	public static RequestPostProcessor como(int idUsuario, String rol) {
		return SecurityMockMvcRequestPostProcessors.user(String.valueOf(idUsuario)).roles(rol);
	}

}
