package br.com.condomix.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** O pagamento de uma cobrança foi registrado (pelo boleto/Pix, sozinho, ou à mão pelo síndico). */
public record CobrancaPaga(UUID condominioId, UUID cobrancaId, UUID unidadeId, String descricao, BigDecimal valorPago,
		LocalDate data, boolean peloBoletoOuPix) {
}
