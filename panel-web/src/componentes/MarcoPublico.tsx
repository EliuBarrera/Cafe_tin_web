import type { ReactNode } from 'react'

/** Pantallas sin sesión: portada de la marca a un lado y el formulario al otro. */
export function MarcoPublico({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <main className="marco-publico">
      <section className="cartel" aria-label="Cafetín UCV">
        {/* Línea de ruta decorativa que cruza la portada */}
        <svg className="cartel-ruta" viewBox="0 0 800 400" preserveAspectRatio="none" aria-hidden="true">
          <path d="M-10 318 C 110 300, 190 272, 290 292 S 470 330, 560 240 S 690 62, 820 30" />
        </svg>
        <div className="marca-panel">
          <span className="insignia-marca" aria-hidden="true">
            <img src="/logo-cafetin.png" alt="" width="72" height="69" />
          </span>
          <span>
            <span className="marca">Cafetín UCV</span>
            <span className="marca-subtitulo">Panel de la cafetería</span>
          </span>
        </div>
        <div>
          <p className="cartel-titulo">
            Pide.
            <br />
            Recoge.
          </p>
          <p className="cartel-script">sin hacer fila</p>
        </div>
        <p className="cartel-pie">Despacho, inventario e ingresos de tu cafetín en un solo lugar.</p>
      </section>
      <div className="publico-contenido">
        <div className="tarjeta">
          <h1>{titulo}</h1>
          {children}
        </div>
      </div>
    </main>
  )
}
