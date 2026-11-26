/**
 * O Safari ignora a altura e o arredondamento da lista nativa: sem a aparência padrão, ela fica igual aos outros
 * campos (mesma altura e borda), com a seta desenhada aqui.
 */
export const SELECAO = 'appearance-none bg-no-repeat pr-10'
export const SETA = {
  backgroundImage: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%2364748b' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E")`,
  backgroundPosition: 'right 0.75rem center',
  backgroundSize: '1.1rem',
}
