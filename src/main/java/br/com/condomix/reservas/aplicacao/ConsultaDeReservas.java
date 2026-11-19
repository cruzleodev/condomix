package br.com.condomix.reservas.aplicacao;

import br.com.condomix.acesso.CadastroParaReservas;
import br.com.condomix.acesso.CadastroParaReservas.UnidadeParaReserva;
import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.reservas.aplicacao.AgendaDeReservas.Solicitante;
import br.com.condomix.reservas.dominio.Espaco;
import br.com.condomix.reservas.dominio.PeriodoDoEspaco;
import br.com.condomix.reservas.dominio.Reserva;
import br.com.condomix.reservas.dominio.SituacaoDaReserva;
import br.com.condomix.reservas.persistencia.EspacoRepository;
import br.com.condomix.reservas.persistencia.ReservaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** A agenda de um espaço e as listas de reservas (minhas e de todo o condomínio). */
@Service
@Transactional(readOnly = true)
public class ConsultaDeReservas {

	/** A agenda mostra no máximo este tanto de dias de uma vez. */
	private static final int DIAS_NA_AGENDA = 62;

	/** "Minhas reservas" mostra também as dos últimos dias. */
	private static final int DIAS_PARA_TRAS = 30;

	private final EspacoRepository espacos;

	private final ReservaRepository reservas;

	private final CadastroParaReservas cadastro;

	private final Clock relogio;

	public ConsultaDeReservas(EspacoRepository espacos, ReservaRepository reservas, CadastroParaReservas cadastro, Clock relogio) {
		this.espacos = espacos;
		this.reservas = reservas;
		this.cadastro = cadastro;
		this.relogio = relogio;
	}

	public enum SituacaoDoHorario {
		LIVRE,
		/** Reservado por outra unidade. */
		OCUPADO,
		/** Reservado por uma unidade de quem está vendo. */
		DA_MINHA_UNIDADE,
		/** Já começou, ou está fora da antecedência do espaço. */
		INDISPONIVEL
	}

	/** @param unidade só para a gestão (o morador vê só que está ocupado) */
	public record HorarioNaAgenda(String nome, LocalTime inicio, LocalTime fim, SituacaoDoHorario situacao, UUID reservaId,
			String unidade) {
	}

	public record DiaNaAgenda(LocalDate data, List<HorarioNaAgenda> horarios) {
	}

	/** Uma reserva com os nomes, para as telas. */
	public record ReservaDetalhada(Reserva reserva, String espaco, String unidade, LocalDate cancelavelSemCustoAte,
			boolean aconteceu) {
	}

	public List<DiaNaAgenda> agenda(UUID espacoId, LocalDate de, LocalDate ate, Solicitante quem) {
		if (ate.isBefore(de) || ChronoUnit.DAYS.between(de, ate) >= DIAS_NA_AGENDA) {
			throw RegraDeNegocioException.invalido("Peça a agenda de até " + DIAS_NA_AGENDA + " dias de cada vez");
		}
		Espaco espaco = espacos.findById(espacoId).orElseThrow(() -> new NaoEncontradoException("Espaço não encontrado"));
		LocalDateTime agora = LocalDateTime.now(relogio);
		LocalDate hoje = agora.toLocalDate();
		LocalDate primeiro = hoje.plusDays(espaco.getRegras().antecedenciaMinimaDias());
		LocalDate ultimo = hoje.plusDays(espaco.getRegras().antecedenciaMaximaDias());
		Set<UUID> minhas = unidadesDe(quem, hoje);
		Map<UUID, String> nomes = quem.gestao() ? nomesDasUnidades() : Map.of();
		Map<LocalDate, List<Reserva>> porDia = reservas
				.findByEspacoIdAndDataBetweenAndSituacaoOrderByDataAscInicioAsc(espacoId, de, ate, SituacaoDaReserva.CONFIRMADA)
				.stream().collect(Collectors.groupingBy(Reserva::getData));
		List<DiaNaAgenda> dias = new ArrayList<>();
		for (LocalDate dia = de; !dia.isAfter(ate); dia = dia.plusDays(1)) {
			List<Reserva> doDia = porDia.getOrDefault(dia, List.of());
			boolean dentroDoPrazo = !dia.isBefore(primeiro) && !dia.isAfter(ultimo) && espaco.isAtivo();
			List<HorarioNaAgenda> horarios = new ArrayList<>();
			for (PeriodoDoEspaco p : espaco.getPeriodos()) {
				Reserva r = doDia.stream().filter(x -> x.sobrepoe(p.inicio(), p.fim())).findFirst().orElse(null);
				if (r != null) {
					horarios.add(new HorarioNaAgenda(p.nome(), p.inicio(), p.fim(), minhas.contains(r.getUnidadeId()) ? SituacaoDoHorario.DA_MINHA_UNIDADE
							: SituacaoDoHorario.OCUPADO, quem.gestao() || minhas.contains(r.getUnidadeId()) ? r.getId() : null,
							nomes.get(r.getUnidadeId())));
				}
				else {
					boolean comecou = dia.equals(hoje) && !agora.toLocalTime().isBefore(p.inicio());
					horarios.add(new HorarioNaAgenda(p.nome(), p.inicio(), p.fim(), dentroDoPrazo && !comecou ? SituacaoDoHorario.LIVRE
							: SituacaoDoHorario.INDISPONIVEL, null, null));
				}
			}
			dias.add(new DiaNaAgenda(dia, horarios));
		}
		return dias;
	}

	/** As reservas das minhas unidades, dos últimos 30 dias em diante. */
	public List<ReservaDetalhada> minhas(UUID usuarioId) {
		LocalDate hoje = LocalDate.now(relogio);
		Map<UUID, String> unidades = cadastro.unidadesDoUsuario(usuarioId, hoje).stream()
				.collect(Collectors.toMap(UnidadeParaReserva::id, UnidadeParaReserva::descricao));
		if (unidades.isEmpty()) {
			return List.of();
		}
		return detalhar(reservas.findByUnidadeIdInAndDataGreaterThanEqualOrderByDataAscInicioAsc(unidades.keySet(),
				hoje.minusDays(DIAS_PARA_TRAS)), unidades);
	}

	/** Todas as reservas do condomínio no período, para a gestão. */
	public List<ReservaDetalhada> doCondominio(LocalDate de, LocalDate ate) {
		if (ate.isBefore(de) || ChronoUnit.DAYS.between(de, ate) > 366) {
			throw RegraDeNegocioException.invalido("Escolha um período de até um ano");
		}
		return detalhar(reservas.findByDataBetweenOrderByDataAscInicioAsc(de, ate), nomesDasUnidades());
	}

	/** Uma reserva, com os nomes (logo depois de reservar). */
	public ReservaDetalhada detalhada(UUID reservaId) {
		Reserva reserva = reservas.findById(reservaId).orElseThrow(() -> new NaoEncontradoException("Reserva não encontrada"));
		return detalhar(List.of(reserva), nomesDasUnidades()).getFirst();
	}

	private List<ReservaDetalhada> detalhar(List<Reserva> lista, Map<UUID, String> unidades) {
		LocalDate hoje = LocalDate.now(relogio);
		Map<UUID, Espaco> porId = espacos.findAll().stream().collect(Collectors.toMap(Espaco::getId, Function.identity()));
		return lista.stream().map(r -> {
			Espaco e = porId.get(r.getEspacoId());
			return new ReservaDetalhada(r, e == null ? "—" : e.getNome(), unidades.getOrDefault(r.getUnidadeId(), "Unidade desativada"),
					e == null ? r.getData() : r.cancelavelSemCustoAte(e.getRegras().diasParaCancelar()), r.getData().isBefore(hoje));
		}).toList();
	}

	private Set<UUID> unidadesDe(Solicitante quem, LocalDate hoje) {
		return cadastro.unidadesDoUsuario(quem.usuarioId(), hoje).stream().map(UnidadeParaReserva::id).collect(Collectors.toSet());
	}

	private Map<UUID, String> nomesDasUnidades() {
		return cadastro.unidadesAtivas().stream().collect(Collectors.toMap(UnidadeParaReserva::id, UnidadeParaReserva::descricao));
	}

}
