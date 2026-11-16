import type { ReactNode } from 'react'

/** Tarjeta centrada de las pantallas sin sesión. */
export function MarcoPublico({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <main className="marco-publico">
      <div className="tarjeta">
        <div className="marca-panel marca-publica">
          <span className="insignia-marca" aria-hidden="true">
            ☕
          </span>
          <span>
            <span className="marca">Cafetín UCV</span>
            <span className="marca-subtitulo">Pedidos sin fila</span>
          </span>
        </div>
        <h1>{titulo}</h1>
        {children}
      </div>
    </main>
  )
}
