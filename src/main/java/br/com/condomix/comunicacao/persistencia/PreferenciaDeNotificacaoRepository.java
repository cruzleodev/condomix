package br.com.condomix.comunicacao.persistencia;

import br.com.condomix.comunicacao.dominio.PreferenciaDeNotificacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PreferenciaDeNotificacaoRepository extends JpaRepository<PreferenciaDeNotificacao, PreferenciaDeNotificacao.Chave> {

	@Query("select p from PreferenciaDeNotificacao p where p.chave.usuarioId = :usuarioId")
	List<PreferenciaDeNotificacao> doUsuario(UUID usuarioId);

	@org.springframework.data.jpa.repository.Modifying
	@Query("delete from PreferenciaDeNotificacao p where p.chave.usuarioId = :usuarioId")
	int apagarDoUsuario(UUID usuarioId);

}
