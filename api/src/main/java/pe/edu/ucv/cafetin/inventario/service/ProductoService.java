package pe.edu.ucv.cafetin.inventario.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.comun.error.CodigoError;
import pe.edu.ucv.cafetin.inventario.dto.InventarioDto.InventarioResponse;
import pe.edu.ucv.cafetin.inventario.dto.InventarioDto.ProductoActualizarRequest;
import pe.edu.ucv.cafetin.inventario.dto.InventarioDto.ProductoRequest;
import pe.edu.ucv.cafetin.inventario.dto.InventarioDto.ProductoResponse;
import pe.edu.ucv.cafetin.inventario.modelo.Categoria;
import pe.edu.ucv.cafetin.inventario.modelo.Inventario;
import pe.edu.ucv.cafetin.inventario.modelo.Producto;
import pe.edu.ucv.cafetin.inventario.modelo.Proveedor;
import pe.edu.ucv.cafetin.inventario.repository.AlertaStockRepository;
import pe.edu.ucv.cafetin.inventario.repository.CategoriaRepository;
import pe.edu.ucv.cafetin.inventario.repository.InventarioRepository;
import pe.edu.ucv.cafetin.inventario.repository.ProductoRepository;
import pe.edu.ucv.cafetin.inventario.repository.ProveedorRepository;

/**
 * Productos del dueño: alta con stock inicial (RF-15), recarga (RF-18) y ver, editar y deshabilitar (RF-19).
 * El stock solo cambia con la venta (trigger) o la recarga; la edición solo toca el mínimo.
 */
@Service
public class ProductoService {

	private final ProductoRepository productos;
	private final InventarioRepository inventarios;
	private final CategoriaRepository categorias;
	private final ProveedorRepository proveedores;
	private final AlertaStockRepository alertas;

	public ProductoService(ProductoRepository productos, InventarioRepository inventarios,
			CategoriaRepository categorias, ProveedorRepository proveedores, AlertaStockRepository alertas) {
		this.productos = productos;
		this.inventarios = inventarios;
		this.categorias = categorias;
		this.proveedores = proveedores;
		this.alertas = alertas;
	}

	@Transactional(readOnly = true)
	public List<ProductoResponse> listar(Boolean activo) {
		return inventarios.conProducto(activo).stream().map(ProductoService::aRespuesta).toList();
	}

	@Transactional(readOnly = true)
	public ProductoResponse obtener(Integer id) {
		return aRespuesta(inventarioDe(id));
	}

	/** RF-15. Producto e inventario en la misma transacción: si uno falla, no queda ninguno. */
	@Transactional
	public ProductoResponse registrar(ProductoRequest s) {
		String nombre = s.nombre().trim();
		if (productos.existsByNombre(nombre)) {
			throw nombreRepetido(nombre);
		}
		Producto producto = new Producto(categoriaActiva(s.idCategoria()), nombre, s.precio());
		producto.setProveedor(s.idProveedor() == null ? null : proveedorActivo(s.idProveedor()));
		producto.setDescripcion(s.descripcion());
		producto.setImagenUrl(s.imagenUrl());
		productos.save(producto);
		Inventario inventario = inventarios.saveAndFlush(new Inventario(producto, s.stockInicial(), s.stockMinimo()));
		return aRespuesta(inventario);
	}

	/** RF-19. El precio nuevo no toca pedidos anteriores: cada detalle guarda su precio_unitario. */
	@Transactional
	public ProductoResponse actualizar(Integer id, ProductoActualizarRequest s) {
		Inventario inventario = inventarioDe(id);
		Producto producto = inventario.getProducto();
		String nombre = s.nombre().trim();
		if (productos.existsByNombreAndIdNot(nombre, id)) {
			throw nombreRepetido(nombre);
		}
		// Una categoría o proveedor ya asignado se conserva aunque se haya desactivado; uno nuevo debe estar activo.
		if (!producto.getCategoria().getId().equals(s.idCategoria())) {
			producto.setCategoria(categoriaActiva(s.idCategoria()));
		}
		Integer proveedorActual = producto.getProveedor() == null ? null : producto.getProveedor().getId();
		if (s.idProveedor() == null) {
			producto.setProveedor(null);
		} else if (!s.idProveedor().equals(proveedorActual)) {
			producto.setProveedor(proveedorActivo(s.idProveedor()));
		}
		producto.setNombre(nombre);
		producto.setDescripcion(s.descripcion());
		producto.setPrecio(s.precio());
		producto.setImagenUrl(s.imagenUrl());
		inventario.setStockMinimo(s.stockMinimo());
		inventarios.flush();
		return aRespuesta(inventario);
	}

	/** RF-19. Deshabilitado = fuera del catálogo y de nuevos pedidos, sin borrarlo. */
	@Transactional
	public ProductoResponse cambiarEstado(Integer id, boolean activo) {
		Inventario inventario = inventarioDe(id);
		inventario.getProducto().setActivo(activo);
		return aRespuesta(inventario);
	}

	/**
	 * RF-18. Suma unidades (también a un producto deshabilitado) y da por atendidas las alertas abiertas
	 * del producto. Subir el stock no genera alertas: el trigger solo alerta al bajar.
	 */
	@Transactional
	public InventarioResponse recargar(Integer idProducto, int cantidad) {
		Integer idInventario = inventarioDe(idProducto).getId();
		inventarios.recargar(idProducto, cantidad);
		alertas.atenderDeInventario(idInventario);
		Inventario i = inventarioDe(idProducto); // releído: la suma y la fecha las hizo la base
		return new InventarioResponse(idProducto, i.getProducto().getNombre(), i.getCantidadDisponible(),
				i.getStockMinimo(), i.getFechaActualizacion());
	}

	// ------------------------------------------------------------------ ayudas

	private Inventario inventarioDe(Integer idProducto) {
		return inventarios.deProducto(idProducto)
				.orElseThrow(() -> new ApiException.NoEncontrado("No existe el producto " + idProducto));
	}

	private Categoria categoriaActiva(Integer id) {
		return categorias.findById(id).filter(Categoria::isActivo)
				.orElseThrow(() -> new ApiException.ReglaNegocio("La categoría " + id + " no existe o está desactivada"));
	}

	private Proveedor proveedorActivo(Integer id) {
		return proveedores.findById(id).filter(Proveedor::isActivo)
				.orElseThrow(() -> new ApiException.ReglaNegocio("El proveedor " + id + " no existe o está desactivado"));
	}

	private static ApiException nombreRepetido(String nombre) {
		return new ApiException(CodigoError.CONFLICTO, "Ya existe un producto llamado \"" + nombre + "\"");
	}

	private static ProductoResponse aRespuesta(Inventario i) {
		Producto p = i.getProducto();
		Proveedor prov = p.getProveedor();
		return new ProductoResponse(p.getId(), p.getNombre(), p.getDescripcion(), p.getPrecio(),
				p.getCategoria().getId(), p.getCategoria().getNombre(),
				prov == null ? null : prov.getId(), prov == null ? null : prov.getNombre(),
				p.getImagenUrl(), p.isActivo(), i.getCantidadDisponible(), i.getStockMinimo(),
				estadoStock(i), i.getFechaActualizacion());
	}

	/** Misma regla que {@code estado_stock} de {@code v_catalogo_disponible}, que no incluye productos deshabilitados. */
	static String estadoStock(Inventario i) {
		if (i.getCantidadDisponible() == 0) {
			return "AGOTADO";
		}
		return i.getCantidadDisponible() <= i.getStockMinimo() ? "POCAS UNIDADES" : "DISPONIBLE";
	}

}
