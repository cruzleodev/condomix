import type { components } from './schema'

/** Atalhos para os tipos gerados do OpenAPI da API (npm run api:tipos). */
type Esquemas = components['schemas']

export type Sessao = Esquemas['Sessao']
export type CondominioDisponivel = Esquemas['CondominioDisponivel']
export type Perfil = NonNullable<Sessao['perfis']>[number]
export type UsuarioLogado = Esquemas['UsuarioLogado']
export type Cobranca = Esquemas['CobrancaResposta']
export type Documento = Esquemas['DocumentoResposta']
export type PaginaDeDocumentos = Esquemas['PaginaDocumentoResposta']
export type UnidadeDaPessoa = Esquemas['UnidadeDaPessoa']
export type Balancete = Esquemas['DoMes']
export type Mandato = Esquemas['MandatoResposta']
export type InformacoesDoConvite = Esquemas['InformacoesDoConvite']
