import type { components } from './esquema'

type Esquemas = components['schemas']

// openapi-typescript marca como opcionales los campos de respuesta: la API siempre los envía.
export type Usuario = Required<Esquemas['UsuarioResponse']>
export type Rol = Usuario['rol']
export type TokenRespuesta = Required<Omit<Esquemas['TokenResponse'], 'usuario'>> & { usuario: Usuario }
export type CampoError = Esquemas['CampoError']
