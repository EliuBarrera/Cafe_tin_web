package pe.edu.ucv.cafetin.catalogo.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ucv.cafetin.catalogo.dto.ProductoCatalogoResponse;
import pe.edu.ucv.cafetin.catalogo.modelo.CatalogoDisponible;
import pe.edu.ucv.cafetin.catalogo.repository.CatalogoRepository;

/** RF-04: productos activos con su stock, incluidos los AGOTADO (la app los deshabilita). */
@Service
public class CatalogoService {

	private final CatalogoRepository catalogo;

	public CatalogoService(CatalogoRepository catalogo) {
		this.catalogo = catalogo;
	}

	@Transactional(readOnly = true)
	public List<ProductoCatalogoResponse> listar(Integer idCategoria) {
		List<CatalogoDisponible> filas = idCategoria == null ? catalogo.listar() : catalogo.listarPorCategoria(idCategoria);
		return filas.stream().map(CatalogoService::aRespuesta).toList();
	}

	private static ProductoCatalogoResponse aRespuesta(CatalogoDisponible c) {
		return new ProductoCatalogoResponse(c.getIdProducto(), c.getNombre(), c.getDescripcion(), c.getPrecio(),
				c.getCategoria(), c.getCantidadDisponible(), c.getEstadoStock());
	}

}
