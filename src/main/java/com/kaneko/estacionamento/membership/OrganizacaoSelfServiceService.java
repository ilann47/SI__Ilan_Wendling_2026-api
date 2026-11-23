package com.kaneko.estacionamento.membership;

import com.kaneko.estacionamento.membership.dto.MembershipResponse;
import com.kaneko.estacionamento.membership.dto.OrganizacaoPropriaResponse;
import com.kaneko.estacionamento.organizacao.OrganizacaoService;
import com.kaneko.estacionamento.organizacao.dto.CriarOrganizacaoRequest;
import com.kaneko.estacionamento.organizacao.dto.OrganizacaoResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orquestra o bootstrap atomico do tenant pertencente a uma conta global neutra. */
@Service
public class OrganizacaoSelfServiceService {

    private final OrganizacaoService organizacaoService;
    private final MembershipService membershipService;

    public OrganizacaoSelfServiceService(
            OrganizacaoService organizacaoService,
            MembershipService membershipService) {
        this.organizacaoService = organizacaoService;
        this.membershipService = membershipService;
    }

    @Transactional
    public OrganizacaoPropriaResponse criar(Long usuarioId, CriarOrganizacaoRequest request) {
        OrganizacaoResponse organizacao = organizacaoService.criar(request);
        MembershipResponse membership = membershipService.criar(organizacao.id(), usuarioId);
        return new OrganizacaoPropriaResponse(organizacao, membership);
    }
}
