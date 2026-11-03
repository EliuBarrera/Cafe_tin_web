const CLAVE = 'cafetin.token'

let tokenEnMemoria: string | null = null

// El navegador puede bloquear localStorage: sin él la sesión dura hasta recargar la página.
export function leerToken(): string | null {
  try {
    return localStorage.getItem(CLAVE) ?? tokenEnMemoria
  } catch {
    return tokenEnMemoria
  }
}

export function guardarToken(token: string) {
  tokenEnMemoria = token
  try {
    localStorage.setItem(CLAVE, token)
  } catch {
    // queda en memoria
  }
}

export function borrarToken() {
  tokenEnMemoria = null
  try {
    localStorage.removeItem(CLAVE)
  } catch {
    // no había nada guardado
  }
}
