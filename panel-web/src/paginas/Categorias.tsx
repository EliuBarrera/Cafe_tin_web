import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useId, useState } from 'react'

import { api } from '../api/cliente'
import type { Categoria } from '../api/tipos'
import { Campo, opcional } from '../componentes/Formulario'
import { MensajeError } from '../componentes/MensajeError'

/** La misma lista alimenta el selector del formulario de productos. */
const CLAVE = ['categorias'] as const

type Panel = { tipo: 'nueva' } | { tipo: 'editar'; categoria: Categoria }

/**
 * Categorías del catálogo (RF-15). La API solo lista las activas: una categoría desactivada desaparece
 * de aquí y no se puede reactivar desde el panel, por eso se pide confirmación.
 */
export function Categorias() {
  const [panel, setPanel] = useState<Panel | null>(null)
  const [aviso, setAviso] = useState('')
  const consulta = useQuery({ queryKey: CLAVE, queryFn: () => api<Categoria[]>('/api/categorias') })
  const categorias = consulta.data

  const abrir = (siguiente: Panel) => {
    setAviso('')
    setPanel(siguiente)
  }

  return (
    <section>
      <div className="encabezado-pagina">
        <h1>Categorías</h1>
        <button type="button" className="boton" onClick={() => abrir({ tipo: 'nueva' })}>
          Nueva categoría
        </button>
      </div>

      <p className="aviso-ok" role="status">
        {aviso}
      </p>

      {panel && (
        <FormularioCategoria
          key={panel.tipo === 'editar' ? panel.categoria.id : 'nueva'}
          categoria={panel.tipo === 'editar' ? panel.categoria : undefined}
          alTerminar={() => setPanel(null)}
        />
      )}

      {consulta.isPending ? (
        <p className="estado-pagina texto-suave">Cargando categorías…</p>
      ) : !categorias ? (
        <div className="estado-pagina">
          <MensajeError error={consulta.error} />
          <button type="button" className="boton" onClick={() => consulta.refetch()}>
            Reintentar
          </button>
        </div>
      ) : categorias.length === 0 ? (
        <p className="estado-pagina texto-suave">Todavía no hay categorías.</p>
      ) : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th scope="col">Categoría</th>
                <th scope="col">Descripción</th>
                <th scope="col" aria-label="Acciones" />
              </tr>
            </thead>
            <tbody>
              {categorias.map((c) => (
                <FilaCategoria
                  key={c.id}
                  categoria={c}
                  alEditar={() => abrir({ tipo: 'editar', categoria: c })}
                  alDesactivar={() => {
                    if (panel?.tipo === 'editar' && panel.categoria.id === c.id) setPanel(null)
                    setAviso(`${c.nombre} quedó desactivada. Ya no aparece en el catálogo ni al crear productos.`)
                  }}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function FilaCategoria({
  categoria: c,
  alEditar,
  alDesactivar,
}: {
  categoria: Categoria
  alEditar: () => void
  alDesactivar: () => void
}) {
  const cliente = useQueryClient()
  const [confirmando, setConfirmando] = useState(false)
  const idTitulo = useId()
  const desactivar = useMutation({
    mutationFn: () => api<Categoria>(`/api/categorias/${c.id}/estado`, { metodo: 'PATCH', cuerpo: { activo: false } }),
    onSuccess: async () => {
      await cliente.invalidateQueries({ queryKey: CLAVE })
      alDesactivar()
    },
  })

  return (
    <tr>
      <td>
        <strong>{c.nombre}</strong>
      </td>
      <td className="texto-suave">{c.descripcion}</td>
      <td>
        {confirmando ? (
          <div className="confirmacion" role="alertdialog" aria-labelledby={idTitulo}>
            <p id={idTitulo}>
              <strong>Desactivar {c.nombre}</strong>
            </p>
            <p>Una categoría desactivada no se puede volver a activar desde el panel.</p>
            <div className="acciones">
              <button type="button" className="boton-texto" disabled={desactivar.isPending} onClick={() => desactivar.mutate()}>
                Sí, desactivar
              </button>
              <button type="button" className="boton-texto" onClick={() => setConfirmando(false)}>
                Cancelar
              </button>
            </div>
            <MensajeError error={desactivar.error} />
          </div>
        ) : (
          <div className="acciones">
            <button type="button" className="boton-texto" onClick={alEditar}>
              Editar
            </button>
            <button type="button" className="boton-texto" onClick={() => setConfirmando(true)}>
              Desactivar
            </button>
          </div>
        )}
      </td>
    </tr>
  )
}

function FormularioCategoria({ categoria, alTerminar }: { categoria?: Categoria; alTerminar: () => void }) {
  const cliente = useQueryClient()
  const [nombre, setNombre] = useState(categoria?.nombre ?? '')
  const [descripcion, setDescripcion] = useState(categoria?.descripcion ?? '')
  const titulo = categoria ? `Editar ${categoria.nombre}` : 'Nueva categoría'
  const idTitulo = useId()

  const guardar = useMutation({
    mutationFn: () => {
      const cuerpo = { nombre: nombre.trim(), descripcion: opcional(descripcion) }
      return categoria
        ? api<Categoria>(`/api/categorias/${categoria.id}`, { metodo: 'PUT', cuerpo })
        : api<Categoria>('/api/categorias', { metodo: 'POST', cuerpo })
    },
    onSuccess: async () => {
      await cliente.invalidateQueries({ queryKey: CLAVE })
      alTerminar()
    },
  })

  return (
    <form
      className="panel-formulario formulario"
      aria-labelledby={idTitulo}
      onSubmit={(e) => {
        e.preventDefault()
        guardar.mutate()
      }}
    >
      <h2 id={idTitulo}>{titulo}</h2>
      <div className="rejilla-campos">
        <Campo etiqueta="Nombre">
          <input value={nombre} onChange={(e) => setNombre(e.target.value)} required maxLength={50} />
        </Campo>
        <Campo etiqueta="Descripción">
          <input value={descripcion} onChange={(e) => setDescripcion(e.target.value)} maxLength={200} />
        </Campo>
      </div>
      <MensajeError error={guardar.error} />
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
