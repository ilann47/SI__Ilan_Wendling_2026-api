package com.kaneko.estacionamento.auditoria;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditoriaService {
    private final RegistroAuditoriaRepository repository;
    private final ContextoAuditoriaProvider contextoProvider;

    public AuditoriaService(RegistroAuditoriaRepository repository, ContextoAuditoriaProvider contextoProvider) {
        this.repository = repository;
        this.contextoProvider = contextoProvider;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(RegistrarAuditoriaCommand command) {
        repository.saveAndFlush(RegistroAuditoria.registrar(command, contextoProvider.atual()));
    }
}
