import { useQuery } from '@tanstack/react-query'
import { api, dados } from './cliente'

/**
 * Até onde as datas podem voltar, usado como mínimo nos calendários:
 * - `data`: o dia em que o condomínio aderiu ao Condomix (posse de mandatos);
 * - `inicioDoHistorico`: o caixa aceita até 12 meses antes da adesão, a partir do dia 1º.
 * Não muda, então fica em cache enquanto o app estiver aberto.
 */
export function useAdesao(): { data?: string; inicioDoHistorico?: string } {
  const adesao = useQuery({
    queryKey: ['condominio', 'adesao'],
    queryFn: async () => dados(await api.GET('/api/condominio/adesao')),
    staleTime: Infinity,
  })
  return adesao.data ?? {}
}
