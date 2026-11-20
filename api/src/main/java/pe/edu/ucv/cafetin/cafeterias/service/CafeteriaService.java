package pe.edu.ucv.cafetin.cafeterias.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ucv.cafetin.cafeterias.dto.CafeteriaResponse;
import pe.edu.ucv.cafetin.cafeterias.modelo.Cafeteria;
import pe.edu.ucv.cafetin.cafeterias.repository.CafeteriaRepository;
import pe.edu.ucv.cafetin.comun.error.ApiException;

@Service
@Transactional(readOnly = true)
public class CafeteriaService {

	private final CafeteriaRepository cafeterias;

	public CafeteriaService(CafeteriaRepository cafeterias) {
		this.cafeterias = cafeterias;
	}

	/** Las que atienden ahora (activas), por nombre: el estudiante elige una en la app. */
	public List<CafeteriaResponse> listarActivas() {
		return cafeterias.findByActivoTrueOrderByNombreAsc().stream().map(CafeteriaService::aRespuesta).toList();
	}

	public CafeteriaResponse obtener(Integer id) {
		return cafeterias.findById(id).map(CafeteriaService::aRespuesta)
				.orElseThrow(() -> new ApiException.NoEncontrado("No existe la cafetería " + id));
	}

	/** Para el estudiante: una cafetería inexistente o desactivada se trata como inexistente (404). */
	public void exigirActiva(Integer id) {
		if (!cafeterias.existsByIdAndActivoTrue(id)) {
			throw new ApiException.NoEncontrado("No existe la cafetería " + id);
		}
	}

	public static CafeteriaResponse aRespuesta(Cafeteria c) {
		return new CafeteriaResponse(c.getId(), c.getNombre(), c.getUbicacion());
	}

}
