package br.com.condomix.comunicacao.persistencia;

import br.com.condomix.comunicacao.dominio.ChavesDoPush;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChavesDoPushRepository extends JpaRepository<ChavesDoPush, Integer> {
}
