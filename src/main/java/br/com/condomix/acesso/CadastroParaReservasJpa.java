package br.com.condomix.acesso;

import br.com.condomix.acesso.persistencia.UnidadeRepository;
import br.com.condomix.acesso.persistencia.VinculoUnidadeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class CadastroParaReservasJpa implements CadastroParaReservas {

	/** Quem usa o espaço: dependentes (ex.: filhos) não reservam. */
	private static final Set<TipoVinculo> QUEM_RESERVA = EnumSet.of(TipoVinculo.PROPRIETARIO, TipoVinculo.INQUILINO, TipoVinculo.MORADOR);

	private final UnidadeRepository unidades;

	private final VinculoUnidadeRepository vinculos;

	CadastroParaReservasJpa(UnidadeRepository unidades, VinculoUnidadeRepository vinculos) {
		this.unidades = unidades;
		this.vinculos = vinculos;
	}

	@Override
	public List<UnidadeParaReserva> unidadesDoUsuario(UUID usuarioId, LocalDate data) {
		return vinculos.vigentesDoUsuario(usuarioId, data).stream()
				.filter(v -> QUEM_RESERVA.contains(v.getTipo()) && v.getUnidade().isAtivo())
				.map(VinculoUnidade::getUnidade)
				.distinct()
				.sorted(Comparator.comparing(Unidade::descricao))
				.map(u -> new UnidadeParaReserva(u.getId(), u.descricao()))
				.toList();
	}

	@Override
	public List<UnidadeParaReserva> unidadesAtivas() {
		return unidades.todasEmOrdem().stream()
				.filter(Unidade::isAtivo)
				.map(u -> new UnidadeParaReserva(u.getId(), u.descricao()))
				.toList();
	}

}
