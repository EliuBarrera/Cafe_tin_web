package pe.edu.ucv.cafetin.soporte;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

/**
 * Datos de prueba propios de cada test (el perfil {@code test} no carga el seed V2). Inserta por SQL
 * para no depender de repositorios de otros módulos. Los nombres llevan un sufijo único porque
 * todos los tests comparten el mismo contenedor. Sin cafetería explícita, todo es de la cafetería 1
 * (la que crea V11); para probar el aislamiento entre cafeterías, {@link #cafeteria()} crea otra.
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

	/** Cafetería que crea V11 para los datos que ya existían. */
	public static final int CAFETERIA_PRINCIPAL = 1;

	public int cafeteria() {
		return jdbc.queryForObject("INSERT INTO cafeteria (nombre) VALUES (?) RETURNING id_cafeteria",
				Integer.class, "Cafetería " + siguiente());
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

	public int trabajador(String nombre, String apellidos) {
		return trabajador(CAFETERIA_PRINCIPAL, nombre, apellidos);
	}

	public int trabajador(int idCafeteria, String nombre, String apellidos) {
		int n = siguiente();
		Integer id = jdbc.queryForObject("""
				INSERT INTO usuario (nombre, apellidos, correo, contrasena, tipo_usuario)
				VALUES (?, ?, ?, 'x', 'TRABAJADOR') RETURNING id_usuario""",
				Integer.class, nombre, apellidos, "trab" + n + "@cafetin.pe");
		jdbc.update("INSERT INTO trabajador (id_usuario, codigo_empleado, turno, id_cafeteria) VALUES (?, ?, 'MANANA', ?)",
				id, "T" + n, idCafeteria);
		return id;
	}

	public int dueno() {
		return dueno(CAFETERIA_PRINCIPAL);
	}

	public int dueno(int idCafeteria) {
		Integer id = jdbc.queryForObject("""
				INSERT INTO usuario (nombre, apellidos, correo, contrasena, tipo_usuario)
				VALUES ('Rosa', 'Prueba', ?, 'x', 'DUENO') RETURNING id_usuario""",
				Integer.class, "dueno" + siguiente() + "@cafetin.pe");
		jdbc.update("INSERT INTO dueno (id_usuario, id_cafeteria) VALUES (?, ?)", id, idCafeteria);
		return id;
	}

	public int categoria() {
		return categoria(CAFETERIA_PRINCIPAL);
	}

	public int categoria(int idCafeteria) {
		return jdbc.queryForObject("INSERT INTO categoria (nombre, id_cafeteria) VALUES (?, ?) RETURNING id_categoria",
				Integer.class, "Cat " + siguiente(), idCafeteria);
	}

	public int proveedor() {
		return proveedor(CAFETERIA_PRINCIPAL);
	}

	public int proveedor(int idCafeteria) {
		return jdbc.queryForObject("""
				INSERT INTO proveedor (nombre, telefono, id_cafeteria) VALUES (?, '044123456', ?)
				RETURNING id_proveedor""", Integer.class, "Proveedor " + siguiente(), idCafeteria);
	}

	public void desactivarCategoria(int idCategoria) {
		jdbc.update("UPDATE categoria SET activo = FALSE WHERE id_categoria = ?", idCategoria);
	}

	/** Producto activo con su inventario (stock mínimo 5), de la cafetería de su categoría. */
	public int producto(int idCategoria, String precio, int stock) {
		Integer id = jdbc.queryForObject("""
				INSERT INTO producto (id_categoria, nombre, descripcion, precio, id_cafeteria)
				SELECT ?, ?, 'De prueba', ?, id_cafeteria FROM categoria WHERE id_categoria = ?
				RETURNING id_producto""",
				Integer.class, idCategoria, "Producto " + siguiente(), new BigDecimal(precio), idCategoria);
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
