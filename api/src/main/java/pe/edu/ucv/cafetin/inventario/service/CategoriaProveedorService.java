package pe.edu.ucv.cafetin.inventario.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.comun.error.CodigoError;
import pe.edu.ucv.cafetin.inventario.dto.InventarioDto.CategoriaRequest;
import pe.edu.ucv.cafetin.inventario.dto.InventarioDto.CategoriaResponse;
import pe.edu.ucv.cafetin.inventario.dto.InventarioDto.ProveedorRequest;
import pe.edu.ucv.cafetin.inventario.dto.InventarioDto.ProveedorResponse;
import pe.edu.ucv.cafetin.inventario.modelo.Categoria;
import pe.edu.ucv.cafetin.inventario.modelo.Proveedor;
import pe.edu.ucv.cafetin.inventario.repository.CategoriaRepository;
import pe.edu.ucv.cafetin.inventario.repository.ProveedorRepository;

/**
 * Categorías (lectura para todos, RF-04; escritura del dueño, RF-15) y proveedores (RF-15).
 * Desactivar no borra ni afecta a los productos que ya los usan; solo impide asignarlos a otros.
 */
@Service
public class CategoriaProveedorService {

	private final CategoriaRepository categorias;
	private final ProveedorRepository proveedores;

	public CategoriaProveedorService(CategoriaRepository categorias, ProveedorRepository proveedores) {
		this.categorias = categorias;
		this.proveedores = proveedores;
	}

	// ------------------------------------------------------------ categorías

	@Transactional(readOnly = true)
	public List<CategoriaResponse> listarCategorias() {
		return categorias.findByActivoTrueOrderByNombreAsc().stream().map(CategoriaProveedorService::aRespuesta).toList();
	}

	@Transactional
	public CategoriaResponse crearCategoria(CategoriaRequest s) {
		String nombre = s.nombre().trim();
		if (categorias.existsByNombre(nombre)) {
			throw repetido("una categoría", nombre);
		}
		return aRespuesta(categorias.saveAndFlush(new Categoria(nombre, s.descripcion())));
	}

	@Transactional
	public CategoriaResponse actualizarCategoria(Integer id, CategoriaRequest s) {
		Categoria categoria = categoria(id);
		String nombre = s.nombre().trim();
		if (categorias.existsByNombreAndIdNot(nombre, id)) {
			throw repetido("una categoría", nombre);
		}
		categoria.setNombre(nombre);
		categoria.setDescripcion(s.descripcion());
		return aRespuesta(categorias.saveAndFlush(categoria));
	}

	@Transactional
	public CategoriaResponse cambiarEstadoCategoria(Integer id, boolean activo) {
		Categoria categoria = categoria(id);
		categoria.setActivo(activo);
		return aRespuesta(categoria);
	}

	// ----------------------------------------------------------- proveedores

	/** Todos, también los desactivados: el dueño los gestiona desde aquí. */
	@Transactional(readOnly = true)
	public List<ProveedorResponse> listarProveedores() {
		return proveedores.findAllByOrderByNombreAsc().stream().map(CategoriaProveedorService::aRespuesta).toList();
	}

	@Transactional
	public ProveedorResponse crearProveedor(ProveedorRequest s) {
		String nombre = s.nombre().trim();
		if (proveedores.existsByNombre(nombre)) {
			throw repetido("un proveedor", nombre);
		}
		Proveedor proveedor = new Proveedor(nombre, s.telefono());
		proveedor.setCorreo(s.correo());
		proveedor.setDireccion(s.direccion());
		return aRespuesta(proveedores.saveAndFlush(proveedor));
	}

	@Transactional
	public ProveedorResponse actualizarProveedor(Integer id, ProveedorRequest s) {
		Proveedor proveedor = proveedor(id);
		String nombre = s.nombre().trim();
		if (proveedores.existsByNombreAndIdNot(nombre, id)) {
			throw repetido("un proveedor", nombre);
		}
		proveedor.setNombre(nombre);
		proveedor.setTelefono(s.telefono());
		proveedor.setCorreo(s.correo());
		proveedor.setDireccion(s.direccion());
		return aRespuesta(proveedores.saveAndFlush(proveedor));
	}

	@Transactional
	public ProveedorResponse cambiarEstadoProveedor(Integer id, boolean activo) {
		Proveedor proveedor = proveedor(id);
		proveedor.setActivo(activo);
		return aRespuesta(proveedor);
	}

	// ------------------------------------------------------------------ ayudas

	private Categoria categoria(Integer id) {
		return categorias.findById(id).orElseThrow(() -> new ApiException.NoEncontrado("No existe la categoría " + id));
	}

	private Proveedor proveedor(Integer id) {
		return proveedores.findById(id).orElseThrow(() -> new ApiException.NoEncontrado("No existe el proveedor " + id));
	}

	private static ApiException repetido(String que, String nombre) {
		return new ApiException(CodigoError.CONFLICTO, "Ya existe " + que + " llamado \"" + nombre + "\"");
	}

	private static CategoriaResponse aRespuesta(Categoria c) {
		return new CategoriaResponse(c.getId(), c.getNombre(), c.getDescripcion(), c.isActivo());
	}

	private static ProveedorResponse aRespuesta(Proveedor p) {
		return new ProveedorResponse(p.getId(), p.getNombre(), p.getTelefono(), p.getCorreo(), p.getDireccion(), p.isActivo());
	}

}
