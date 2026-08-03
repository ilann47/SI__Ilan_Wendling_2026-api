package com.kaneko.estacionamento.common.config;

import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.evento.EventoService;
import com.kaneko.estacionamento.evento.PoliticaReentrada;
import com.kaneko.estacionamento.evento.dto.AbrirVendasEventoRequest;
import com.kaneko.estacionamento.evento.dto.AtualizarEventoRequest;
import com.kaneko.estacionamento.evento.dto.CriarEventoRequest;
import com.kaneko.estacionamento.evento.dto.PublicarEventoRequest;
import com.kaneko.estacionamento.geografia.Cidade;
import com.kaneko.estacionamento.geografia.CidadeRepository;
import com.kaneko.estacionamento.geografia.CidadeService;
import com.kaneko.estacionamento.geografia.Estado;
import com.kaneko.estacionamento.geografia.EstadoRepository;
import com.kaneko.estacionamento.geografia.EstadoService;
import com.kaneko.estacionamento.geografia.Pais;
import com.kaneko.estacionamento.geografia.PaisRepository;
import com.kaneko.estacionamento.geografia.PaisService;
import com.kaneko.estacionamento.geografia.dto.CidadeRequest;
import com.kaneko.estacionamento.geografia.dto.EstadoRequest;
import com.kaneko.estacionamento.geografia.dto.PaisRequest;
import com.kaneko.estacionamento.instalacoes.CategoriaSetor;
import com.kaneko.estacionamento.instalacoes.InstalacoesService;
import com.kaneko.estacionamento.instalacoes.SetorService;
import com.kaneko.estacionamento.instalacoes.VagaService;
import com.kaneko.estacionamento.instalacoes.dto.CriarPatioInstalacaoRequest;
import com.kaneko.estacionamento.instalacoes.dto.CriarSetorRequest;
import com.kaneko.estacionamento.instalacoes.dto.CriarVagaItemRequest;
import com.kaneko.estacionamento.instalacoes.dto.ImportarVagasRequest;
import com.kaneko.estacionamento.inventario.alocacao.AlocacaoPatioEventoService;
import com.kaneko.estacionamento.inventario.alocacao.dto.CriarAlocacaoPatioEventoRequest;
import com.kaneko.estacionamento.inventario.produto.CategoriaProduto;
import com.kaneko.estacionamento.inventario.produto.DireitoProduto;
import com.kaneko.estacionamento.inventario.produto.LotePrecoService;
import com.kaneko.estacionamento.inventario.produto.ProdutoEstacionamentoService;
import com.kaneko.estacionamento.inventario.produto.dto.CriarLotePrecoRequest;
import com.kaneko.estacionamento.inventario.produto.dto.CriarProdutoEstacionamentoRequest;
import com.kaneko.estacionamento.inventario.produto.dto.PublicarProdutoRequest;
import com.kaneko.estacionamento.membership.MembershipService;
import com.kaneko.estacionamento.organizacao.OrganizacaoRepository;
import com.kaneko.estacionamento.organizacao.OrganizacaoService;
import com.kaneko.estacionamento.organizacao.dto.CriarOrganizacaoRequest;
import com.kaneko.estacionamento.organizacao.venue.VenueService;
import com.kaneko.estacionamento.organizacao.venue.dto.CriarVenueRequest;
import com.kaneko.estacionamento.usuario.Usuario;
import com.kaneko.estacionamento.usuario.UsuarioRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoDataSeedService {

    public static final String DOCUMENTO_ORGANIZACAO = "11222333000181";
    private static final String FUSO = "America/Sao_Paulo";

    private final OrganizacaoRepository organizacoes;
    private final UsuarioRepository usuarios;
    private final PaisRepository paises;
    private final EstadoRepository estados;
    private final CidadeRepository cidades;
    private final PaisService paisService;
    private final EstadoService estadoService;
    private final CidadeService cidadeService;
    private final OrganizacaoService organizacaoService;
    private final MembershipService membershipService;
    private final VenueService venueService;
    private final InstalacoesService instalacoesService;
    private final SetorService setorService;
    private final VagaService vagaService;
    private final EventoService eventoService;
    private final AlocacaoPatioEventoService alocacaoService;
    private final ProdutoEstacionamentoService produtoService;
    private final LotePrecoService loteService;

    public DemoDataSeedService(
            OrganizacaoRepository organizacoes,
            UsuarioRepository usuarios,
            PaisRepository paises,
            EstadoRepository estados,
            CidadeRepository cidades,
            PaisService paisService,
            EstadoService estadoService,
            CidadeService cidadeService,
            OrganizacaoService organizacaoService,
            MembershipService membershipService,
            VenueService venueService,
            InstalacoesService instalacoesService,
            SetorService setorService,
            VagaService vagaService,
            EventoService eventoService,
            AlocacaoPatioEventoService alocacaoService,
            ProdutoEstacionamentoService produtoService,
            LotePrecoService loteService) {
        this.organizacoes = organizacoes;
        this.usuarios = usuarios;
        this.paises = paises;
        this.estados = estados;
        this.cidades = cidades;
        this.paisService = paisService;
        this.estadoService = estadoService;
        this.cidadeService = cidadeService;
        this.organizacaoService = organizacaoService;
        this.membershipService = membershipService;
        this.venueService = venueService;
        this.instalacoesService = instalacoesService;
        this.setorService = setorService;
        this.vagaService = vagaService;
        this.eventoService = eventoService;
        this.alocacaoService = alocacaoService;
        this.produtoService = produtoService;
        this.loteService = loteService;
    }

    @Transactional
    public DemoDataSeedResult seed(String adminLogin) {
        if (organizacoes.existsByDocumento(DOCUMENTO_ORGANIZACAO)) {
            return DemoDataSeedResult.alreadyPresent();
        }
        Usuario admin = usuarios.findByLoginIgnoreCase(normalizarLogin(adminLogin))
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Usuario administrador do bootstrap nao encontrado."));
        Cidade cidade = garantirCidadeDemo();

        var organizacao = organizacaoService.criar(new CriarOrganizacaoRequest(
                DOCUMENTO_ORGANIZACAO,
                "Kaneko Eventos Demonstracao Ltda",
                "Kaneko Eventos Demo",
                "BRL",
                FUSO,
                "BR-PR",
                "DEMO"));
        Long organizacaoId = organizacao.id();
        var membership = membershipService.criar(organizacaoId, admin.getId());

        var venue = venueService.criar(organizacaoId, new CriarVenueRequest(
                "Arena Kaneko Demo",
                "Avenida Brasil",
                "2026",
                "Entrada pelo portao principal",
                "Centro",
                "85810-000",
                cidade.getId(),
                FUSO));
        var patio = instalacoesService.criar(organizacaoId,
                new CriarPatioInstalacaoRequest(
                        "Patio Principal Demo",
                        "Avenida Brasil",
                        "2026",
                        "Acesso lateral",
                        "Centro",
                        "85810-000",
                        cidade.getId(),
                        120,
                        100,
                        "Patio criado pelo seed local de demonstracao.",
                        true));
        var setor = setorService.criar(organizacaoId, patio.id(),
                new CriarSetorRequest(
                        "A",
                        "Setor A - Visitantes",
                        100,
                        Set.of(CategoriaSetor.COMUM, CategoriaSetor.PCD, CategoriaSetor.VIP)));
        vagaService.importar(
                organizacaoId, setor.id(), new ImportarVagasRequest(vagasDemo()));

        OffsetDateTime agora = OffsetDateTime.now(ZoneId.of(FUSO))
                .truncatedTo(ChronoUnit.MINUTES);
        OffsetDateTime inicio = agora.minusHours(1);
        OffsetDateTime fim = agora.plusDays(30);
        var evento = eventoService.criar(
                organizacaoId,
                admin.getId(),
                "demo-evento-v1",
                new CriarEventoRequest(
                        "Festival Kaneko Demo",
                        venue.id(),
                        inicio,
                        fim,
                        FUSO,
                        "KANEKO-DEMO-EVENTO"));
        evento = eventoService.atualizar(
                organizacaoId,
                evento.id(),
                evento.version(),
                new AtualizarEventoRequest(PoliticaReentrada.REENTRADA_PERMITIDA));

        var alocacao = alocacaoService.criar(
                organizacaoId,
                admin.getId(),
                evento.id(),
                "demo-alocacao-v1",
                new CriarAlocacaoPatioEventoRequest(
                        patio.id(), inicio, fim, 100, 90, 10));
        var produto = produtoService.criar(
                organizacaoId,
                admin.getId(),
                evento.id(),
                "demo-produto-v1",
                new CriarProdutoEstacionamentoRequest(
                        "Estacionamento Comum Demo",
                        CategoriaProduto.COMUM,
                        alocacao.id(),
                        inicio,
                        fim,
                        DireitoProduto.ESTACIONAMENTO_EVENTO,
                        90,
                        List.of("ACESSO_QR", "REENTRADA"),
                        List.of("VALIDO_SOMENTE_NO_EVENTO")));
        var lote = loteService.criar(
                organizacaoId,
                admin.getId(),
                produto.id(),
                "demo-lote-v1",
                new CriarLotePrecoRequest(
                        "Lote Demonstracao",
                        new BigDecimal("35.00"),
                        "BRL",
                        inicio,
                        fim.minusHours(1),
                        90,
                        1));
        produto = produtoService.publicar(
                organizacaoId,
                produto.id(),
                produto.version(),
                new PublicarProdutoRequest(true));
        evento = eventoService.publicar(
                organizacaoId,
                evento.id(),
                evento.version(),
                new PublicarEventoRequest(true));
        eventoService.abrirVendas(
                organizacaoId,
                evento.id(),
                evento.version(),
                new AbrirVendasEventoRequest(null));

        return new DemoDataSeedResult(
                true,
                organizacaoId,
                membership.id(),
                venue.id(),
                patio.id(),
                setor.id(),
                evento.id(),
                alocacao.id(),
                produto.id(),
                lote.id());
    }

    private Cidade garantirCidadeDemo() {
        Pais pais = paises.findByNomeIgnoreCase("Brasil")
                .orElseGet(() -> paisService.criar(new PaisRequest(
                        "Brasil", "BRA", "076", "Brasileira", "Real", "55", true)));
        Estado estado = estados.findByPaisIdAndUfIgnoreCase(pais.getId(), "PR")
                .orElseGet(() -> estadoService.criar(new EstadoRequest(
                        "Parana", "PR", pais.getId(), true)));
        return cidades.findByEstadoIdAndNomeIgnoreCase(estado.getId(), "Cascavel")
                .orElseGet(() -> cidadeService.criar(new CidadeRequest(
                        "Cascavel", "45", "4104808", estado.getId(), true)));
    }

    private static List<CriarVagaItemRequest> vagasDemo() {
        List<CriarVagaItemRequest> vagas = new ArrayList<>();
        for (int numero = 1; numero <= 10; numero++) {
            vagas.add(new CriarVagaItemRequest(
                    "A-%03d".formatted(numero),
                    CategoriaSetor.COMUM,
                    false,
                    "Fileira A"));
        }
        vagas.add(new CriarVagaItemRequest(
                "PCD-001", CategoriaSetor.PCD, true, "Proxima a entrada"));
        vagas.add(new CriarVagaItemRequest(
                "VIP-001", CategoriaSetor.VIP, false, "Area coberta"));
        return List.copyOf(vagas);
    }

    private static String normalizarLogin(String adminLogin) {
        if (adminLogin == null || adminLogin.isBlank()) {
            throw new IllegalArgumentException("O login do administrador demo e obrigatorio.");
        }
        return adminLogin.trim();
    }
}
