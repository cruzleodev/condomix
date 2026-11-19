package br.com.condomix.administracao;

import br.com.condomix.faturamento.FaturasDosClientes;
import br.com.condomix.faturamento.FaturasDosClientes.FaturaDeCliente;
import br.com.condomix.faturamento.FaturasDosClientes.PagamentoDaFatura;
import br.com.condomix.faturamento.FaturasDosClientes.ResumoDaRotina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** As faturas da assinatura de todos os clientes. */
@Tag(name = "Plataforma: faturas")
@RestController
@RequestMapping("/api/plataforma/faturas")
class FaturasDaPlataformaController {

	private final FaturasDosClientes faturas;

	FaturasDaPlataformaController(FaturasDosClientes faturas) {
		this.faturas = faturas;
	}

	@Operation(summary = "Faturas de todos os clientes (ou de um, com condominioId), as mais recentes primeiro")
	@GetMapping
	List<FaturaDeCliente> listar(@RequestParam(required = false) UUID condominioId) {
		return condominioId == null ? faturas.todas() : faturas.doCliente(condominioId);
	}

	@Operation(summary = "2ª via da fatura (Pix e boleto), para mandar ao síndico")
	@GetMapping("/{id}/pagamento")
	PagamentoDaFatura segundaVia(@PathVariable UUID id) {
		return faturas.segundaVia(id);
	}

	@Operation(summary = "Emitir de novo uma fatura que falhou (depois de corrigir o motivo, ex.: o CNPJ)")
	@PostMapping("/{id}/nova-tentativa")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void tentarDeNovo(@PathVariable UUID id) {
		faturas.tentarDeNovo(id);
	}

	@Operation(summary = "Cancelar (perdoar) a fatura",
			description = "O boleto é cancelado no gateway. O período não é cobrado de novo. Se era a fatura que mantinha "
					+ "o acesso suspenso, o acesso volta.")
	@PostMapping("/{id}/cancelamento")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void cancelar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
		faturas.cancelar(id, UUID.fromString(jwt.getSubject()));
	}

	@Operation(summary = "Rodar agora a rotina de cobrança (fim dos testes, faturas novas e suspensões)",
			description = "Ela já roda sozinha de hora em hora; isto só adianta.")
	@PostMapping("/rotina")
	ResumoDaRotina rodarRotina() {
		return faturas.rodarRotinaAgora();
	}

}
