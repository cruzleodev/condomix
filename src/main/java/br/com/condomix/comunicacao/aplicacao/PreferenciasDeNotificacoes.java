package br.com.condomix.comunicacao.aplicacao;

import br.com.condomix.comunicacao.dominio.Canal;
import br.com.condomix.comunicacao.dominio.GrupoDeNotificacao;
import br.com.condomix.comunicacao.dominio.PreferenciaDeNotificacao;
import br.com.condomix.comunicacao.persistencia.PreferenciaDeNotificacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** O que cada pessoa quer receber por e-mail e push. Sem nada gravado, recebe tudo. */
@Service
@Transactional
public class PreferenciasDeNotificacoes {

	private final PreferenciaDeNotificacaoRepository repositorio;

	public PreferenciasDeNotificacoes(PreferenciaDeNotificacaoRepository repositorio) {
		this.repositorio = repositorio;
	}

	public record Escolha(GrupoDeNotificacao grupo, Canal canal, boolean ativo) {
	}

	/** Todos os grupos e canais, com o que a pessoa escolheu (ou ligado, se nunca mexeu). */
	@Transactional(readOnly = true)
	public List<Escolha> doUsuario(UUID usuarioId) {
		Map<String, Boolean> gravadas = repositorio.doUsuario(usuarioId).stream()
				.collect(Collectors.toMap(p -> p.getGrupo() + ":" + p.getCanal(), PreferenciaDeNotificacao::isAtivo));
		return Arrays.stream(GrupoDeNotificacao.values())
				.flatMap(g -> Arrays.stream(Canal.values()).map(c -> new Escolha(g, c, gravadas.getOrDefault(g + ":" + c, true))))
				.toList();
	}

	public List<Escolha> alterar(UUID usuarioId, List<Escolha> escolhas) {
		for (Escolha e : escolhas) {
			repositorio.findById(new PreferenciaDeNotificacao.Chave(usuarioId, e.grupo(), e.canal()))
					.ifPresentOrElse(p -> p.definir(e.ativo()),
							() -> repositorio.save(new PreferenciaDeNotificacao(usuarioId, e.grupo(), e.canal(), e.ativo())));
		}
		return doUsuario(usuarioId);
	}

	/** Os canais que a pessoa desligou para o grupo. */
	@Transactional(readOnly = true)
	public Set<Canal> desligados(UUID usuarioId, GrupoDeNotificacao grupo) {
		Set<Canal> desligados = EnumSet.noneOf(Canal.class);
		repositorio.doUsuario(usuarioId).stream().filter(p -> p.getGrupo() == grupo && !p.isAtivo())
				.forEach(p -> desligados.add(p.getCanal()));
		return desligados;
	}

}
