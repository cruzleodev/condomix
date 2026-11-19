package br.com.condomix.acesso;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** O que o módulo de reservas precisa do cadastro do condomínio atual (SOLID: I). Só leitura. */
public interface CadastroParaReservas {

	/** Unidades ativas em que o usuário pode reservar na data: ele é proprietário, inquilino ou morador. */
	List<UnidadeParaReserva> unidadesDoUsuario(UUID usuarioId, LocalDate data);

	/** Todas as unidades ativas, em ordem (a gestão reserva para qualquer uma). */
	List<UnidadeParaReserva> unidadesAtivas();

	/** @param descricao "Bloco A · 101", como aparece nas telas */
	record UnidadeParaReserva(UUID id, String descricao) {
	}

}
