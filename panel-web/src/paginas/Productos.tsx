import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useId, useState, type FormEvent } from 'react'

import { api } from '../api/cliente'
import type {
  Categoria,
  EstadoStock,
  Producto,
  ProductoAlta,
  ProductoEdicion,
  Proveedor,
  StockRecargado,
} from '../api/tipos'
import { Campo, opcional } from '../componentes/Formulario'
import { MensajeError } from '../componentes/MensajeError'

const CLAVE = ['productos'] as const

type Filtro = '' | 'true' | 'false'

const FILTROS: { valor: Filtro; texto: string }[] = [
  { valor: '', texto: 'Todos' },
  { valor: 'true', texto: 'Activos' },
  { valor: 'false', texto: 'Deshabilitados' },
]

const TEXTO_STOCK: Record<EstadoStock, string> = {
  DISPONIBLE: 'Disponible',
  'POCAS UNIDADES': 'Pocas unidades',
  AGOTADO: 'Agotado',
}

const CLASE_STOCK: Record<EstadoStock, string> = {
  DISPONIBLE: 'insignia-ok',
  'POCAS UNIDADES': 'insignia-aviso',
  AGOTADO: 'insignia-error',
}

type Panel = { tipo: 'nuevo' } | { tipo: 'editar'; producto: Producto } | { tipo: 'recargar'; producto: Producto }

const soles = (monto: number) => `S/ ${monto.toFixed(2)}`

/**
 * Productos del dueño: alta con stock inicial (RF-15), recarga (RF-18) y edición y baja (RF-19).
 * Deshabilitar oculta el producto del catálogo sin borrarlo; el stock solo cambia con la recarga.
 */
export function Productos() {
  const [filtro, setFiltro] = useState<Filtro>('')
  const [panel, setPanel] = useState<Panel | null>(null)
  const [aviso, setAviso] = useState('')
  const idFiltro = useId()

  const consulta = useQuery({
    queryKey: [...CLAVE, filtro],
    queryFn: () => api<Producto[]>(`/api/productos${filtro ? `?activo=${filtro}` : ''}`),
  })
  const productos = consulta.data

  const abrir = (siguiente: Panel) => {
    setAviso('')
    setPanel(siguiente)
  }

  return (
    <section>
      <div className="encabezado-pagina">
        <h1>Productos</h1>
        <button type="button" className="boton" onClick={() => abrir({ tipo: 'nuevo' })}>
          Nuevo producto
        </button>
      </div>

      <p className="aviso-ok" role="status">
        {aviso}
      </p>

      {panel?.tipo === 'nuevo' && <FormularioProducto alTerminar={() => setPanel(null)} />}
      {panel?.tipo === 'editar' && (
        <FormularioProducto key={panel.producto.id} producto={panel.producto} alTerminar={() => setPanel(null)} />
      )}
      {panel?.tipo === 'recargar' && (
        <FormularioRecarga
          key={panel.producto.id}
          producto={panel.producto}
          alCancelar={() => setPanel(null)}
          alRecargar={(r) => {
            setPanel(null)
            setAviso(`${r.producto}: ahora hay ${r.cantidadDisponible} unidades.`)
          }}
        />
      )}

      <div className="filtros">
        <label htmlFor={idFiltro}>Mostrar</label>
        <select id={idFiltro} value={filtro} onChange={(e) => setFiltro(e.target.value as Filtro)}>
          {FILTROS.map((f) => (
            <option key={f.valor} value={f.valor}>
              {f.texto}
            </option>
          ))}
        </select>
      </div>

      {consulta.isPending ? (
        <p className="estado-pagina texto-suave">Cargando productos…</p>
      ) : !productos ? (
        <div className="estado-pagina">
          <MensajeError error={consulta.error} />
          <button type="button" className="boton" onClick={() => consulta.refetch()}>
            Reintentar
          </button>
        </div>
      ) : productos.length === 0 ? (
        <p className="estado-pagina texto-suave">
          {filtro ? 'No hay productos con este filtro.' : 'Todavía no hay productos.'}
        </p>
      ) : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th scope="col">Producto</th>
                <th scope="col" className="numero">
                  Precio
                </th>
                <th scope="col">Stock</th>
                <th scope="col">Estado</th>
                <th scope="col" aria-label="Acciones" />
              </tr>
            </thead>
            <tbody>
              {productos.map((p) => (
                <FilaProducto
                  key={p.id}
                  producto={p}
                  alEditar={() => abrir({ tipo: 'editar', producto: p })}
                  alRecargar={() => abrir({ tipo: 'recargar', producto: p })}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function FilaProducto({
  producto: p,
  alEditar,
  alRecargar,
}: {
  producto: Producto
  alEditar: () => void
  alRecargar: () => void
}) {
  const cliente = useQueryClient()
  const cambiarEstado = useMutation({
    mutationFn: (activo: boolean) => api<Producto>(`/api/productos/${p.id}/estado`, { metodo: 'PATCH', cuerpo: { activo } }),
    onSettled: () => cliente.invalidateQueries({ queryKey: CLAVE }),
  })

  return (
    <tr className={p.activo ? undefined : 'fila-inactiva'}>
      <td>
        <strong>{p.nombre}</strong>
        <span className="texto-suave subtexto">
          {p.categoria}
          {p.proveedor && ` · ${p.proveedor}`}
        </span>
      </td>
      <td className="numero">{soles(p.precio)}</td>
      <td>
        <strong className="numero-stock">{p.cantidadDisponible}</strong>{' '}
        <span className="texto-suave">mín. {p.stockMinimo}</span>
      </td>
      <td>
        <span className={`insignia ${CLASE_STOCK[p.estadoStock]}`}>{TEXTO_STOCK[p.estadoStock]}</span>
        {!p.activo && <span className="insignia insignia-apagada">Deshabilitado</span>}
      </td>
      <td>
        <div className="acciones">
          <button type="button" className="boton-texto" onClick={alEditar}>
            Editar
          </button>
          <button type="button" className="boton-texto" onClick={alRecargar}>
            Recargar
          </button>
          <button
            type="button"
            className="boton-texto"
            disabled={cambiarEstado.isPending}
            onClick={() => cambiarEstado.mutate(!p.activo)}
          >
            {p.activo ? 'Deshabilitar' : 'Habilitar'}
          </button>
        </div>
        <MensajeError error={cambiarEstado.error} />
      </td>
    </tr>
  )
}

interface Campos {
  nombre: string
  descripcion: string
  precio: string
  idCategoria: string
  idProveedor: string
  imagenUrl: string
  stockInicial: string
  stockMinimo: string
}

function camposDe(p?: Producto): Campos {
  return {
    nombre: p?.nombre ?? '',
    descripcion: p?.descripcion ?? '',
    precio: p ? p.precio.toFixed(2) : '',
    idCategoria: p ? String(p.idCategoria) : '',
    idProveedor: p?.idProveedor != null ? String(p.idProveedor) : '',
    imagenUrl: p?.imagenUrl ?? '',
    stockInicial: '',
    stockMinimo: p ? String(p.stockMinimo) : '',
  }
}

/** Alta (RF-15) o edición (RF-19). En la edición no se toca el stock: eso es la recarga. */
function FormularioProducto({ producto, alTerminar }: { producto?: Producto; alTerminar: () => void }) {
  const cliente = useQueryClient()
  const [c, setC] = useState<Campos>(() => camposDe(producto))
  const titulo = producto ? `Editar ${producto.nombre}` : 'Nuevo producto'
  const idTitulo = useId()

  const categorias = useQuery({ queryKey: ['categorias'], queryFn: () => api<Categoria[]>('/api/categorias') })
  const proveedores = useQuery({ queryKey: ['proveedores'], queryFn: () => api<Proveedor[]>('/api/proveedores') })

  // Un producto puede tener asignada una categoría o un proveedor ya desactivado: se conserva como opción.
  const opcionesCategoria = conActual(
    (categorias.data ?? []).map((x) => ({ id: x.id, nombre: x.nombre })),
    producto && { id: producto.idCategoria, nombre: producto.categoria },
  )
  const opcionesProveedor = conActual(
    (proveedores.data ?? []).filter((x) => x.activo).map((x) => ({ id: x.id, nombre: x.nombre })),
    producto?.idProveedor != null ? { id: producto.idProveedor, nombre: producto.proveedor ?? '' } : undefined,
  )

  const guardar = useMutation({
    mutationFn: () => {
      const comunes = {
        nombre: c.nombre.trim(),
        descripcion: opcional(c.descripcion),
        precio: Number(c.precio),
        idCategoria: Number(c.idCategoria),
        idProveedor: c.idProveedor ? Number(c.idProveedor) : undefined,
        imagenUrl: opcional(c.imagenUrl),
        stockMinimo: Number(c.stockMinimo),
      }
      if (producto) {
        return api<Producto>(`/api/productos/${producto.id}`, { metodo: 'PUT', cuerpo: comunes satisfies ProductoEdicion })
      }
      const alta: ProductoAlta = { ...comunes, stockInicial: Number(c.stockInicial) }
      return api<Producto>('/api/productos', { metodo: 'POST', cuerpo: alta })
    },
    onSuccess: async () => {
      await cliente.invalidateQueries({ queryKey: CLAVE })
      alTerminar()
    },
  })

  const cambiar = (campo: keyof Campos) => (e: { target: { value: string } }) =>
    setC((previo) => ({ ...previo, [campo]: e.target.value }))

  const enviar = (e: FormEvent) => {
    e.preventDefault()
    guardar.mutate()
  }

  return (
    <form className="panel-formulario formulario" aria-labelledby={idTitulo} onSubmit={enviar}>
      <h2 id={idTitulo}>{titulo}</h2>
      <div className="rejilla-campos">
        <Campo etiqueta="Nombre" ancho>
          <input value={c.nombre} onChange={cambiar('nombre')} required maxLength={100} />
        </Campo>
        <Campo etiqueta="Descripción" ancho>
          <input value={c.descripcion} onChange={cambiar('descripcion')} maxLength={250} />
        </Campo>
        <Campo etiqueta="Precio (S/)">
          <input type="number" inputMode="decimal" min="0.01" step="0.01" value={c.precio} onChange={cambiar('precio')} required />
        </Campo>
        <Campo etiqueta="Categoría">
          <select value={c.idCategoria} onChange={cambiar('idCategoria')} required>
            <option value="" disabled>
              Elige una categoría
            </option>
            {opcionesCategoria.map((o) => (
              <option key={o.id} value={o.id}>
                {o.nombre}
              </option>
            ))}
          </select>
        </Campo>
        <Campo etiqueta="Proveedor">
          <select value={c.idProveedor} onChange={cambiar('idProveedor')}>
            <option value="">Sin proveedor (preparación propia)</option>
            {opcionesProveedor.map((o) => (
              <option key={o.id} value={o.id}>
                {o.nombre}
              </option>
            ))}
          </select>
        </Campo>
        {!producto && (
          <Campo etiqueta="Stock inicial">
            <input type="number" inputMode="numeric" min="0" step="1" value={c.stockInicial} onChange={cambiar('stockInicial')} required />
          </Campo>
        )}
        <Campo etiqueta="Stock mínimo">
          <input type="number" inputMode="numeric" min="0" step="1" value={c.stockMinimo} onChange={cambiar('stockMinimo')} required />
        </Campo>
        <Campo etiqueta="URL de imagen" ancho>
          <input type="url" value={c.imagenUrl} onChange={cambiar('imagenUrl')} maxLength={300} />
        </Campo>
      </div>
      <MensajeError error={guardar.error ?? categorias.error ?? proveedores.error} />
      <div className="acciones-formulario">
        <button type="submit" className="boton" disabled={guardar.isPending}>
          Guardar
        </button>
        <button type="button" className="boton boton-secundario" onClick={alTerminar}>
          Cancelar
        </button>
      </div>
    </form>
  )
}

/** RF-18. Suma unidades a las disponibles; la API marca como atendidas las alertas del producto. */
function FormularioRecarga({
  producto,
  alCancelar,
  alRecargar,
}: {
  producto: Producto
  alCancelar: () => void
  alRecargar: (r: StockRecargado) => void
}) {
  const cliente = useQueryClient()
  const [cantidad, setCantidad] = useState('')
  const idTitulo = useId()
  const unidades = Number(cantidad)
  const valida = Number.isInteger(unidades) && unidades > 0

  const recargar = useMutation({
    mutationFn: () =>
      api<StockRecargado>(`/api/inventario/${producto.id}/recarga`, { metodo: 'PATCH', cuerpo: { cantidad: unidades } }),
    onSuccess: async (r) => {
      await cliente.invalidateQueries({ queryKey: CLAVE })
      alRecargar(r)
    },
  })

  return (
    <form
      className="panel-formulario formulario"
      aria-labelledby={idTitulo}
      onSubmit={(e) => {
        e.preventDefault()
        if (valida) recargar.mutate()
      }}
    >
      <h2 id={idTitulo}>Recargar {producto.nombre}</h2>
      <p className="texto-suave">
        Hay {producto.cantidadDisponible} unidades (mínimo {producto.stockMinimo}).
      </p>
      <div className="rejilla-campos">
        <Campo etiqueta="Unidades a agregar">
          <input
            type="number"
            inputMode="numeric"
            min="1"
            step="1"
            value={cantidad}
            onChange={(e) => setCantidad(e.target.value)}
            required
          />
        </Campo>
      </div>
      <MensajeError error={recargar.error} />
      <div className="acciones-formulario">
        <button type="submit" className="boton" disabled={!valida || recargar.isPending}>
          Recargar stock
        </button>
        <button type="button" className="boton boton-secundario" onClick={alCancelar}>
          Cancelar
        </button>
      </div>
    </form>
  )
}

function conActual(opciones: { id: number; nombre: string }[], actual?: { id: number; nombre: string }) {
  return actual && !opciones.some((o) => o.id === actual.id) ? [actual, ...opciones] : opciones
}
