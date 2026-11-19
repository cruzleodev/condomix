package br.com.condomix.reservas.aplicacao;

import br.com.condomix.acesso.CadastroParaReservas;
import br.com.condomix.acesso.CadastroParaReservas.UnidadeParaReserva;
import br.com.condomix.compartilhado.FormatoBrasileiro;
import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.financeiro.CobrancasParaReservas;
import br.com.condomix.financeiro.CobrancasParaReservas.ResultadoDoCancelamento;
import br.com.condomix.reservas.dominio.Espaco;
import br.com.condomix.reservas.dominio.PeriodoDoEspaco;
import br.com.condomix.reservas.dominio.Reserva;
import br.com.condomix.reservas.dominio.SituacaoDaReserva;
import br.com.condomix.reservas.persistencia.EspacoRepository;
import br.com.condomix.reservas.persistencia.ReservaRepository;
import br.com.condomix.reservas.regras.PedidoDeReserva;
import br.com.condomix.reservas.regras.RegraDeReserva;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Casos de uso: reservar e cancelar. A reserva vale na hora, se passar em todas as regras e o horário estiver livre.
 * Quando o espaço cobra, o aluguel vira cobrança avulsa na mesma transação (tudo ou nada).
 */
@Service
@Transactional
public class AgendaDeReservas {

	private final EspacoRepository espacos;

	private final ReservaRepository reservas;

	private final List<RegraDeReserva> regras;

	private final CadastroParaReservas cadastro;

	private final CobrancasParaReservas cobrancas;

	private final Clock relogio;

	public AgendaDeReservas(EspacoRepository espacos, ReservaRepository reservas, List<RegraDeReserva> regras,
			CadastroParaReservas cadastro, CobrancasParaReservas cobrancas, Clock relogio) {
		this.espacos = espacos;
		this.reservas = reservas;
		this.regras = regras;
		this.cadastro = cadastro;
		this.cobrancas = cobrancas;
		this.relogio = relogio;
	}

	/** Quem pede: o usuário e se ele é da gestão (reserva para qualquer unidade e cancela qualquer reserva). */
	public record Solicitante(UUID usuarioId, boolean gestao) {
	}

	public record NovaReserva(UUID espacoId, LocalDate data, String periodo, UUID unidadeId, String observacao) {
	}

	/** O que aconteceu com o aluguel ao cancelar. */
	public enum AluguelNoCancelamento {
		SEM_ALUGUEL, CANCELADO, MANTIDO, JA_PAGO
	}

	public record ReservaCancelada(Reserva reserva, AluguelNoCancelamento aluguel) {
	}

	public Reserva reservar(NovaReserva nova, Solicitante quem) {
		LocalDateTime agora = LocalDateTime.now(relogio);
		LocalDate hoje = agora.toLocalDate();
		UnidadeParaReserva unidade = unidadesPermitidas(quem, hoje).stream()
				.filter(u -> u.id().equals(nova.unidadeId())).findFirst()
				.orElseThrow(() -> RegraDeNegocioException.invalido(quem.gestao()
						? "Unidade não encontrada ou desativada"
						: "Você só pode reservar para uma unidade em que mora ou é proprietário"));
		// Trava o espaço: duas pessoas pedindo o mesmo horário ao mesmo tempo passam uma de cada vez.
		Espaco espaco = espacos.paraReservar(nova.espacoId()).orElseThrow(() -> new NaoEncontradoException("Espaço não encontrado"));
		if (!espaco.isAtivo()) {
			throw RegraDeNegocioException.conflito("Este espaço não está aceitando reservas");
		}
		PeriodoDoEspaco periodo = espaco.periodo(nova.periodo())
				.orElseThrow(() -> RegraDeNegocioException.invalido("Este espaço não tem o horário \"" + nova.periodo() + "\""));
		if (nova.data() == null) {
			throw RegraDeNegocioException.invalido("Informe o dia da reserva");
		}
		if (nova.data().equals(hoje) && !agora.toLocalTime().isBefore(periodo.inicio())) {
			throw RegraDeNegocioException.invalido("Este horário de hoje já começou.");
		}
		var pedido = new PedidoDeReserva(espaco, unidade.id(), nova.data(), hoje);
		for (RegraDeReserva regra : regras) {
			regra.recusa(pedido).ifPresent(motivo -> {
				throw RegraDeNegocioException.invalido(motivo);
			});
		}
		boolean ocupado = reservas.findByEspacoIdAndDataAndSituacao(espaco.getId(), nova.data(), SituacaoDaReserva.CONFIRMADA)
				.stream().anyMatch(r -> r.sobrepoe(periodo.inicio(), periodo.fim()));
		if (ocupado) {
			throw RegraDeNegocioException.conflito("Este horário já está reservado. Escolha outro horário ou outro dia.");
		}
		Reserva reserva = reservas.save(new Reserva(espaco, periodo, unidade.id(), nova.data(), nova.observacao(), quem.usuarioId()));
		if (espaco.cobraAluguel()) {
			String descricao = "Aluguel: " + espaco.getNome() + ", " + FormatoBrasileiro.data(nova.data()) + " (" + periodo.nome() + ")";
			reserva.cobradaPor(cobrancas.cobrarAluguel(unidade.id(), descricao, espaco.getValorAluguel(), nova.data(), quem.usuarioId()));
		}
		return reserva;
	}

	/**
	 * Cancela até o dia da reserva. No prazo do espaço, o aluguel é cancelado; depois dele, continua devido. A gestão
	 * pode dispensar o aluguel (ex.: o espaço ficou interditado).
	 */
	public ReservaCancelada cancelar(UUID reservaId, String motivo, boolean dispensarAluguel, Solicitante quem) {
		LocalDate hoje = LocalDate.now(relogio);
		Reserva reserva = reservas.findById(reservaId).orElseThrow(() -> new NaoEncontradoException("Reserva não encontrada"));
		if (!quem.gestao() && unidadesPermitidas(quem, hoje).stream().noneMatch(u -> u.id().equals(reserva.getUnidadeId()))) {
			throw new NaoEncontradoException("Reserva não encontrada");
		}
		if (reserva.getSituacao() == SituacaoDaReserva.CANCELADA) {
			throw RegraDeNegocioException.conflito("Esta reserva já foi cancelada");
		}
		if (reserva.getData().isBefore(hoje)) {
			throw RegraDeNegocioException.conflito("Esta reserva já aconteceu e não pode mais ser cancelada");
		}
		Espaco espaco = espacos.findById(reserva.getEspacoId()).orElseThrow();
		boolean noPrazo = !hoje.isAfter(reserva.cancelavelSemCustoAte(espaco.getRegras().diasParaCancelar()));
		boolean semCusto = noPrazo || (quem.gestao() && dispensarAluguel);
		AluguelNoCancelamento aluguel = AluguelNoCancelamento.SEM_ALUGUEL;
		if (reserva.getCobrancaId() != null) {
			aluguel = semCusto ? cancelarAluguel(reserva, motivo) : AluguelNoCancelamento.MANTIDO;
		}
		reserva.cancelar(Instant.now(relogio), quem.usuarioId(), motivo, aluguel == AluguelNoCancelamento.MANTIDO);
		return new ReservaCancelada(reserva, aluguel);
	}

	/** As unidades para as quais a pessoa pode reservar: a gestão, todas; o morador, as suas. */
	@Transactional(readOnly = true)
	public List<UnidadeParaReserva> unidadesPermitidas(Solicitante quem, LocalDate hoje) {
		return quem.gestao() ? cadastro.unidadesAtivas() : cadastro.unidadesDoUsuario(quem.usuarioId(), hoje);
	}

	private AluguelNoCancelamento cancelarAluguel(Reserva reserva, String motivo) {
		var resultado = cobrancas.cancelarAluguel(reserva.getCobrancaId(),
				"Reserva cancelada" + (motivo == null || motivo.isBlank() ? "" : ": " + motivo.strip()));
		return resultado == ResultadoDoCancelamento.JA_PAGA ? AluguelNoCancelamento.JA_PAGO : AluguelNoCancelamento.CANCELADO;
	}

}
