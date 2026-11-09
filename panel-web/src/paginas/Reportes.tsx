import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useId, useState, type ReactNode } from 'react'

import { api } from '../api/cliente'
import type { IngresoPeriodo, PaginaVentas, Periodo, ProductoVendido } from '../api/tipos'
import { MensajeError } from '../componentes/MensajeError'

const TAMANO_PAGINA = 20
const LIMITE_RANKING = 10

const PERIODOS: { valor: Periodo; texto: string }[] = [
  { valor: 'DIA', texto: 'Por día' },
  { valor: 'SEMANA', texto: 'Por semana' },
  { valor: 'MES', texto: 'Por mes' },
]

const soles = (monto: number) => `S/ ${monto.toFixed(2)}`

/** 'yyyy-MM-dd' como fecha local: `new Date('2026-11-02')` sería medianoche UTC, el día anterior en Lima. */
function fechaLocal(iso: string) {
  const [a, m, d] = iso.split('-').map(Number)
  return new Date(a, m - 1, d)
}

const formatoDia = new Intl.DateTimeFormat('es-PE', { weekday: 'short', day: 'numeric', month: 'short' })
const formatoSemana = new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'short', year: 'numeric' })
const formatoMes = new Intl.DateTimeFormat('es-PE', { month: 'long', year: 'numeric' })
const formatoFechaHora = new Intl.DateTimeFormat('es-PE', { dateStyle: 'short', timeStyle: 'short' })

function etiquetaPeriodo(periodo: Periodo, inicio: string) {
  const fecha = fechaLocal(inicio)
  if (periodo === 'SEMANA') return `Semana del ${formatoSemana.format(fecha)}`
  if (periodo === 'MES') {
    const texto = formatoMes.format(fecha)
    return texto.charAt(0).toUpperCase() + texto.slice(1)
  }
  return formatoDia.format(fecha)
}

function consulta(parametros: Record<string, string | number>) {
  const q = new URLSearchParams()
  for (const [clave, valor] of Object.entries(parametros)) {
    if (valor !== '') q.set(clave, String(valor))
  }
  return q.toString()
}

/**
 * Ingresos del dueño: por día, semana o mes (RF-21), más vendidos (RF-22) y ventas (RF-20).
 * Sin fechas, la API toma los últimos 30 días según la fecha de la base (Lima).
 */
export function Reportes() {
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [pagina, setPagina] = useState(0)
  const idDesde = useId()
  const idHasta = useId()

  const rangoInvalido = desde !== '' && hasta !== '' && desde > hasta

  // Un rango nuevo vuelve a la primera página de ventas.
  const cambiarRango = (fijar: (valor: string) => void) => (valor: string) => {
    fijar(valor)
    setPagina(0)
  }

  return (
    <section>
      <div className="encabezado-pagina">
        <h1>Reportes</h1>
      </div>

      <div className="filtros">
        <label htmlFor={idDesde}>Desde</label>
        <input id={idDesde} type="date" value={desde} max={hasta || undefined} onChange={(e) => cambiarRango(setDesde)(e.target.value)} />
        <label htmlFor={idHasta}>Hasta</label>
        <input id={idHasta} type="date" value={hasta} min={desde || undefined} onChange={(e) => cambiarRango(setHasta)(e.target.value)} />
        {(desde || hasta) && (
          <button
            type="button"
            className="boton-texto"
            onClick={() => {
              setDesde('')
              setHasta('')
              setPagina(0)
            }}
          >
            Últimos 30 días
          </button>
        )}
      </div>
      {!desde && !hasta && <p className="texto-suave nota-filtros">Sin fechas se muestran los últimos 30 días.</p>}
      {rangoInvalido && (
        <p className="mensaje-error nota-filtros" role="alert">
          "Desde" no puede ser posterior a "Hasta".
        </p>
      )}

      <div className="reportes">
        <Ingresos desde={desde} hasta={hasta} activo={!rangoInvalido} />
        <MasVendidos />
        <Ventas desde={desde} hasta={hasta} activo={!rangoInvalido} pagina={pagina} alCambiarPagina={setPagina} />
      </div>
    </section>
  )
}

function Seccion({ titulo, extra, children }: { titulo: string; extra?: ReactNode; children: ReactNode }) {
  const id = useId()
  return (
    <section className="panel-reporte" aria-labelledby={id}>
      <div className="encabezado-seccion">
        <h2 id={id}>{titulo}</h2>
        {extra}
      </div>
      {children}
    </section>
  )
}

/** RF-21. Del periodo más antiguo al más reciente; los periodos sin ventas no aparecen. */
function Ingresos({ desde, hasta, activo }: { desde: string; hasta: string; activo: boolean }) {
  const [periodo, setPeriodo] = useState<Periodo>('DIA')
  const idPeriodo = useId()

  const ingresos = useQuery({
    queryKey: ['reportes', 'ingresos', periodo, desde, hasta],
    queryFn: () => api<IngresoPeriodo[]>(`/api/reportes/ingresos?${consulta({ periodo, desde, hasta })}`),
    enabled: activo,
  })

  const filas = ingresos.data ?? []
  const total = filas.reduce((suma, f) => suma + f.ingresoTotal, 0)
  const pedidos = filas.reduce((suma, f) => suma + f.pedidosPagados, 0)

  return (
    <Seccion
      titulo="Ingresos"
      extra={
        <div className="filtros filtros-compactos">
          <label htmlFor={idPeriodo}>Agrupar</label>
          <select id={idPeriodo} value={periodo} onChange={(e) => setPeriodo(e.target.value as Periodo)}>
            {PERIODOS.map((p) => (
              <option key={p.valor} value={p.valor}>
                {p.texto}
              </option>
            ))}
          </select>
        </div>
      }
    >
      <Contenido consulta={ingresos} vacio={filas.length === 0} textoVacio="Sin ventas en este rango.">
        <p className="resumen">
          <strong>{soles(total)}</strong>
          <span>{`${pedidos} pedidos pagados`}</span>
          <span>{`Ticket promedio ${soles(pedidos ? total / pedidos : 0)}`}</span>
        </p>
        <div className="tabla-contenedor">
          <table className="tabla tabla-compacta">
            <thead>
              <tr>
                <th scope="col">Periodo</th>
                <th scope="col" className="numero">
                  Pedidos
                </th>
                <th scope="col" className="numero">
                  Ingreso
                </th>
                <th scope="col" className="numero">
                  Ticket promedio
                </th>
              </tr>
            </thead>
            <tbody>
              {filas.map((f) => (
                <tr key={f.inicio}>
                  <td>{etiquetaPeriodo(periodo, f.inicio)}</td>
                  <td className="numero">{f.pedidosPagados}</td>
                  <td className="numero">{soles(f.ingresoTotal)}</td>
                  <td className="numero">{soles(f.ticketPromedio)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Contenido>
    </Seccion>
  )
}

/** RF-22. Todo el histórico, por unidades vendidas. */
function MasVendidos() {
  const ranking = useQuery({
    queryKey: ['reportes', 'mas-vendidos'],
    queryFn: () => api<ProductoVendido[]>(`/api/reportes/mas-vendidos?limite=${LIMITE_RANKING}`),
  })
  const filas = ranking.data ?? []

  return (
    <Seccion titulo="Más vendidos" extra={<p className="texto-suave">Desde el inicio</p>}>
      <Contenido consulta={ranking} vacio={filas.length === 0} textoVacio="Todavía no hay ventas.">
        <div className="tabla-contenedor">
          <table className="tabla tabla-compacta">
            <thead>
              <tr>
                <th scope="col" className="numero">
                  #
                </th>
                <th scope="col">Producto</th>
                <th scope="col" className="numero">
                  Unidades
                </th>
                <th scope="col" className="numero">
                  Ingreso
                </th>
              </tr>
            </thead>
            <tbody>
              {filas.map((p, i) => (
                <tr key={p.idProducto}>
                  <td className="numero">{i + 1}</td>
                  <td>
                    <span>{p.nombre}</span>
                    <span className="texto-suave subtexto">{p.categoria}</span>
                  </td>
                  <td className="numero">{p.unidadesVendidas}</td>
                  <td className="numero">{soles(p.ingresoGenerado)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Contenido>
    </Seccion>
  )
}

/** RF-20. Cada pago PAGADO, más recientes primero. */
function Ventas({
  desde,
  hasta,
  activo,
  pagina,
  alCambiarPagina,
}: {
  desde: string
  hasta: string
  activo: boolean
  pagina: number
  alCambiarPagina: (pagina: number) => void
}) {
  const ventas = useQuery({
    queryKey: ['reportes', 'ventas', desde, hasta, pagina],
    queryFn: () =>
      api<PaginaVentas>(`/api/reportes/ventas?${consulta({ desde, hasta, pagina, tamano: TAMANO_PAGINA })}`),
    enabled: activo,
    placeholderData: keepPreviousData, // al pasar de página no se vacía la tabla
  })
  const datos = ventas.data

  return (
    <Seccion titulo="Ventas">
      <Contenido consulta={ventas} vacio={!datos || datos.contenido.length === 0} textoVacio="Sin ventas en este rango.">
        {datos && (
          <>
            <div className="tabla-contenedor">
              <table className="tabla tabla-compacta">
                <thead>
                  <tr>
                    <th scope="col">Fecha</th>
                    <th scope="col">Pedido</th>
                    <th scope="col">Estudiante</th>
                    <th scope="col">Método</th>
                    <th scope="col" className="numero">
                      Monto
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {datos.contenido.map((v) => (
                    <tr key={v.idPago}>
                      <td>{formatoFechaHora.format(new Date(v.fecha))}</td>
                      <td>{v.codigoPedido}</td>
                      <td>{v.estudiante}</td>
                      <td>{v.metodoPago}</td>
                      <td className="numero">{soles(v.monto)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="paginacion">
              <button
                type="button"
                className="boton boton-secundario"
                disabled={pagina === 0 || ventas.isFetching}
                onClick={() => alCambiarPagina(pagina - 1)}
              >
                Anterior
              </button>
              <p className="texto-suave">{`Página ${datos.pagina + 1} de ${datos.totalPaginas} · ${datos.totalElementos} ventas`}</p>
              <button
                type="button"
                className="boton boton-secundario"
                disabled={pagina + 1 >= datos.totalPaginas || ventas.isFetching}
                onClick={() => alCambiarPagina(pagina + 1)}
              >
                Siguiente
              </button>
            </div>
          </>
        )}
      </Contenido>
    </Seccion>
  )
}

/** Cargando, error con reintento, vacío o el contenido. */
function Contenido({
  consulta,
  vacio,
  textoVacio,
  children,
}: {
  consulta: { isPending: boolean; isError: boolean; error: unknown; fetchStatus: string; refetch: () => unknown }
  vacio: boolean
  textoVacio: string
  children: ReactNode
}) {
  // Deshabilitada (rango inválido): isPending sin consultar. No se muestra "Cargando…".
  if (consulta.isPending && consulta.fetchStatus === 'idle') return null
  if (consulta.isPending) return <p className="texto-suave">Cargando…</p>
  if (consulta.isError) {
    return (
      <div>
        <MensajeError error={consulta.error} />
        <button type="button" className="boton-texto" onClick={() => consulta.refetch()}>
          Reintentar
        </button>
      </div>
    )
  }
  if (vacio) return <p className="texto-suave">{textoVacio}</p>
  return <>{children}</>
}
