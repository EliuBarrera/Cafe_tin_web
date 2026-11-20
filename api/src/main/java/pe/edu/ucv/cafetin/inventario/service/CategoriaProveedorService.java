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
 * Cada cafetería gestiona las suyas: una de otra cafetería se trata como inexistente (404).
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
	public List<CategoriaResponse> listarCategorias(Integer idCafeteria) {
		return categorias.findByIdCafeteriaAndActivoTrueOrderByNombreAsc(idCafeteria).stream().map(CategoriaProveedorService::aRespuesta).toList();
	}

	@Transactional
	public CategoriaResponse crearCategoria(Integer idCafeteria, CategoriaRequest s) {
		String nombre = s.nombre().trim();
		if (categorias.existsByIdCafeteriaAndNombre(idCafeteria, nombre)) {
			throw repetido("una categoría", nombre);
		}
		return aRespuesta(categorias.saveAndFlush(new Categoria(idCafeteria, nombre, s.descripcion())));
	}

	@Transactional
	public CategoriaResponse actualizarCategoria(Integer idCafeteria, Integer id, CategoriaRequest s) {
		Categoria categoria = categoria(idCafeteria, id);
		String nombre = s.nombre().trim();
		if (categorias.existsByIdCafeteriaAndNombreAndIdNot(idCafeteria, nombre, id)) {
			throw repetido("una categoría", nombre);
		}
		categoria.setNombre(nombre);
		categoria.setDescripcion(s.descripcion());
		return aRespuesta(categorias.saveAndFlush(categoria));
	}

	@Transactional
	public CategoriaResponse cambiarEstadoCategoria(Integer idCafeteria, Integer id, boolean activo) {
		Categoria categoria = categoria(idCafeteria, id);
		categoria.setActivo(activo);
		return aRespuesta(categoria);
	}

	// ----------------------------------------------------------- proveedores

	/** Todos, también los desactivados: el dueño los gestiona desde aquí. */
	@Transactional(readOnly = true)
	public List<ProveedorResponse> listarProveedores(Integer idCafeteria) {
		return proveedores.findByIdCafeteriaOrderByNombreAsc(idCafeteria).stream().map(CategoriaProveedorService::aRespuesta).toList();
	}

	@Transactional
	public ProveedorResponse crearProveedor(Integer idCafeteria, ProveedorRequest s) {
		String nombre = s.nombre().trim();
		if (proveedores.existsByIdCafeteriaAndNombre(idCafeteria, nombre)) {
			throw repetido("un proveedor", nombre);
		}
		Proveedor proveedor = new Proveedor(idCafeteria, nombre, s.telefono());
		proveedor.setCorreo(s.correo());
		proveedor.setDireccion(s.direccion());
		return aRespuesta(proveedores.saveAndFlush(proveedor));
	}

	@Transactional
	public ProveedorResponse actualizarProveedor(Integer idCafeteria, Integer id, ProveedorRequest s) {
		Proveedor proveedor = proveedor(idCafeteria, id);
		String nombre = s.nombre().trim();
		if (proveedores.existsByIdCafeteriaAndNombreAndIdNot(idCafeteria, nombre, id)) {
			throw repetido("un proveedor", nombre);
		}
		proveedor.setNombre(nombre);
		proveedor.setTelefono(s.telefono());
		proveedor.setCorreo(s.correo());
		proveedor.setDireccion(s.direccion());
		return aRespuesta(proveedores.saveAndFlush(proveedor));
	}

	@Transactional
	public ProveedorResponse cambiarEstadoProveedor(Integer idCafeteria, Integer id, boolean activo) {
		Proveedor proveedor = proveedor(idCafeteria, id);
		proveedor.setActivo(activo);
		return aRespuesta(proveedor);
	}

	// ------------------------------------------------------------------ ayudas

	private Categoria categoria(Integer idCafeteria, Integer id) {
		return categorias.findByIdAndIdCafeteria(id, idCafeteria).orElseThrow(() -> new ApiException.NoEncontrado("No existe la categoría " + id));
	}

	private Proveedor proveedor(Integer idCafeteria, Integer id) {
		return proveedores.findByIdAndIdCafeteria(id, idCafeteria).orElseThrow(() -> new ApiException.NoEncontrado("No existe el proveedor " + id));
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
