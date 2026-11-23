package com.kaneko.estacionamento.financeiro;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BaixaFinanceiraRepository extends JpaRepository<BaixaFinanceira, Long> {
    boolean existsByContaPagarId(Long id);
    boolean existsByContaReceberId(Long id);
    Optional<BaixaFinanceira> findByContaPagarIdAndChave(Long id, String chave);
    Optional<BaixaFinanceira> findByContaReceberIdAndChave(Long id, String chave);
    List<BaixaFinanceira> findByContaPagarIdOrderByIdAsc(Long id);
    List<BaixaFinanceira> findByContaReceberIdOrderByIdAsc(Long id);
}
