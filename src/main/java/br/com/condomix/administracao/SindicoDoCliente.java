package br.com.condomix.administracao;

import br.com.condomix.acesso.ConviteGerado;
import br.com.condomix.acesso.IngressoDeSindico;
import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import br.com.condomix.plataforma.ConsultaDeCondominios;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;
import java.util.UUID;

/**
 * Caso de uso: o administrador cuida do síndico de um cliente. Vê quem é, gera um novo convite quando o
 * link se perdeu e registra o novo síndico quando ele muda (o mandato anterior termina na véspera da posse).
 */
@Service
class SindicoDoCliente {

	/** Limite do mandato (Código Civil, art. 1.347). */
	private static final Period MANDATO_MAXIMO = Period.ofYears(2);

	private final IngressoDeSindico sindicos;

	private final ConsultaDeCondominios condominios;

	SindicoDoCliente(IngressoDeSindico sindicos, ConsultaDeCondominios condominios) {
		this.sindicos = sindicos;
		this.condominios = condominios;
	}

	Optional<IngressoDeSindico.SindicoAtual> atual(UUID condominioId) {
		conferirCondominio(condominioId);
		return sindicos.sindicoAtual(condominioId);
	}

	ConviteGerado reconvidar(UUID condominioId, String email, UUID administradorId) {
		conferirCondominio(condominioId);
		return sindicos.reconvidar(condominioId, email, administradorId);
	}

	IngressoDeSindico.SindicoRegistrado trocar(UUID condominioId, IngressoDeSindico.NovoSindico novo, UUID administradorId) {
		conferirCondominio(condominioId);
		conferirMandato(novo.inicioMandato(), novo.fimMandato());
		return sindicos.registrar(condominioId, novo, administradorId);
	}

	static void conferirMandato(LocalDate inicio, LocalDate fim) {
		if (fim.isBefore(inicio) || fim.isAfter(inicio.plus(MANDATO_MAXIMO).minusDays(1))) {
			throw RegraDeNegocioException.invalido("O mandato do síndico deve terminar depois da posse e durar no máximo 2 anos");
		}
	}

	private void conferirCondominio(UUID condominioId) {
		if (!condominios.existe(condominioId)) {
			throw new NaoEncontradoException("Condomínio não encontrado");
		}
	}

}
