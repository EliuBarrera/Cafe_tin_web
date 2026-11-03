import { QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'

import { App } from './App'
import { crearClienteQuery } from './clienteQuery'
import { SesionProvider } from './sesion/SesionProvider'
import './estilos.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={crearClienteQuery()}>
      <SesionProvider>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </SesionProvider>
    </QueryClientProvider>
  </StrictMode>,
)
