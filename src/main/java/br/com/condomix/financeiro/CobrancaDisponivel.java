package br.com.condomix.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma cobrança nova pode ser paga: com o boleto ligado, quando o boleto e o Pix ficam prontos; sem ele, quando a
 * cobrança é lançada. As importadas da administradora não geram este evento.
 */
public record CobrancaDisponivel(UUID condominioId, UUID cobrancaId, UUID unidadeId, String descricao, BigDecimal valor,
		LocalDate vencimento, boolean comBoletoEPix) {
}
