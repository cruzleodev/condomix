package br.com.condomix.acesso.convite;

import br.com.condomix.acesso.ConviteGerado;
import br.com.condomix.acesso.Perfil;
import br.com.condomix.acesso.Pessoa;
import br.com.condomix.acesso.persistencia.ConviteRepository;
import br.com.condomix.acesso.persistencia.PessoaRepository;
import br.com.condomix.compartilhado.EnvioDeEmail;
import br.com.condomix.compartilhado.FormatoBrasileiro;
import br.com.condomix.compartilhado.NaoEncontradoException;
import br.com.condomix.plataforma.ConsultaDeCondominios;
import br.com.condomix.plataforma.ResumoCondominio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import br.com.condomix.compartilhado.RegraDeNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Caso de uso: convidar uma pessoa do condomínio atual para ter acesso. Um convite novo cancela o pendente. */
@Service
@Transactional
public class EmissaoDeConvites {

	private static final Logger log = LoggerFactory.getLogger(EmissaoDeConvites.class);

	private final ConviteRepository convites;

	private final PessoaRepository pessoas;

	private final Clock relogio;

	private final Duration validade;

	private final String enderecoDoApp;

	private final EnvioDeEmail email;

	private final ConsultaDeCondominios condominios;

	public EmissaoDeConvites(ConviteRepository convites, PessoaRepository pessoas, Clock relogio,
			@Value("${condomix.convite.validade:7d}") Duration validade,
			@Value("${condomix.convite.endereco-do-app:http://localhost:5173/convite}") String enderecoDoApp,
			EnvioDeEmail email, ConsultaDeCondominios condominios) {
		this.email = email;
		this.condominios = condominios;
		this.convites = convites;
		this.pessoas = pessoas;
		this.relogio = relogio;
		this.validade = validade;
		this.enderecoDoApp = enderecoDoApp;
	}

	/** @param perfil papel a receber; {@code null} quando o papel vem de um mandato (ex.: síndico) */
	public ConviteGerado convidar(UUID pessoaId, String email, Perfil perfil, UUID criadoPor) {
		Pessoa pessoa = pessoas.findById(pessoaId)
				.orElseThrow(() -> new NaoEncontradoException("Pessoa não encontrada neste condomínio"));
		if (pessoa.getUsuarioId() != null && perfil == null) {
			throw RegraDeNegocioException.conflito(pessoa.getNome() + " já tem acesso ao sistema");
		}
		Instant agora = Instant.now(relogio);
		convites.pendentesDaPessoa(pessoaId).forEach(c -> c.cancelar(agora));

		String codigo = CodigoDeConvite.gerar();
		Convite convite;
		try {
			convite = convites.save(new Convite(pessoa, email, perfil, CodigoDeConvite.impressaoDigital(codigo),
					agora.plus(validade), criadoPor));
		}
		catch (IllegalArgumentException e) {
			throw RegraDeNegocioException.invalido(e.getMessage());
		}
		if (pessoa.getEmail() == null) {
			pessoa.alterarContato(convite.getEmail(), pessoa.getTelefone());
		}
		String link = enderecoDoApp + "?codigo=" + codigo;
		boolean porEmail = this.email.disponivel();
		if (porEmail) {
			enviarDepoisDeGravar(convite, pessoa, link);
		}
		return new ConviteGerado(convite.getId(), convite.getEmail(), link, convite.getExpiraEm(), porEmail);
	}

	/**
	 * O e-mail sai só depois que o convite foi gravado (senão o link poderia não valer). O código não vai para a fila
	 * de eventos: só fica no banco a impressão digital dele. Se o e-mail falhar, quem convidou ainda tem o link.
	 */
	private void enviarDepoisDeGravar(Convite convite, Pessoa pessoa, String link) {
		String condominio = condominios.ativosEntre(Set.of(pessoa.getTenantId())).stream().findFirst()
				.map(ResumoCondominio::nome).orElse("seu condomínio");
		var mensagem = new EnvioDeEmail.MensagemDeEmail(convite.getEmail(), pessoa.getNome(), "Seu acesso ao Condomix: " + condominio,
				"Você foi convidado para acessar o app do " + condominio + " no Condomix: boletos, reservas, documentos e avisos "
						+ "do condomínio. Clique no botão para criar sua senha. O convite vale até "
						+ FormatoBrasileiro.data(LocalDate.ofInstant(convite.getExpiraEm(), relogio.getZone())) + ".",
				link, "Criar minha senha", "Se você não esperava este convite, ignore este e-mail.");
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				try {
					email.enviar(mensagem);
				}
				catch (RuntimeException e) {
					log.warn("Não foi possível enviar o convite por e-mail ({})", e.getMessage());
				}
			}
		});
	}

}
