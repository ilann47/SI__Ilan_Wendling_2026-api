package com.kaneko.estacionamento.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kaneko.estacionamento.evento.EventoService;
import com.kaneko.estacionamento.geografia.CidadeRepository;
import com.kaneko.estacionamento.geografia.CidadeService;
import com.kaneko.estacionamento.geografia.EstadoRepository;
import com.kaneko.estacionamento.geografia.EstadoService;
import com.kaneko.estacionamento.geografia.PaisRepository;
import com.kaneko.estacionamento.geografia.PaisService;
import com.kaneko.estacionamento.instalacoes.InstalacoesService;
import com.kaneko.estacionamento.instalacoes.SetorService;
import com.kaneko.estacionamento.instalacoes.VagaService;
import com.kaneko.estacionamento.inventario.alocacao.AlocacaoPatioEventoService;
import com.kaneko.estacionamento.inventario.produto.LotePrecoService;
import com.kaneko.estacionamento.inventario.produto.ProdutoEstacionamentoService;
import com.kaneko.estacionamento.membership.MembershipService;
import com.kaneko.estacionamento.organizacao.OrganizacaoRepository;
import com.kaneko.estacionamento.organizacao.OrganizacaoService;
import com.kaneko.estacionamento.organizacao.venue.VenueService;
import com.kaneko.estacionamento.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DemoDataSeedServiceTest {

    @Mock private OrganizacaoRepository organizacoes;
    @Mock private UsuarioRepository usuarios;
    @Mock private PaisRepository paises;
    @Mock private EstadoRepository estados;
    @Mock private CidadeRepository cidades;
    @Mock private PaisService paisService;
    @Mock private EstadoService estadoService;
    @Mock private CidadeService cidadeService;
    @Mock private OrganizacaoService organizacaoService;
    @Mock private MembershipService membershipService;
    @Mock private VenueService venueService;
    @Mock private InstalacoesService instalacoesService;
    @Mock private SetorService setorService;
    @Mock private VagaService vagaService;
    @Mock private EventoService eventoService;
    @Mock private AlocacaoPatioEventoService alocacaoService;
    @Mock private ProdutoEstacionamentoService produtoService;
    @Mock private LotePrecoService loteService;

    @InjectMocks
    private DemoDataSeedService seedService;

    @Test
    void naoDuplicaCenarioQuandoOrganizacaoDemoJaExiste() {
        when(organizacoes.existsByDocumento(DemoDataSeedService.DOCUMENTO_ORGANIZACAO))
                .thenReturn(true);

        DemoDataSeedResult resultado = seedService.seed("admin");

        assertThat(resultado.created()).isFalse();
        verifyNoInteractions(
                usuarios, paises, estados, cidades, paisService, estadoService,
                cidadeService, organizacaoService, membershipService, venueService,
                instalacoesService, setorService, vagaService, eventoService,
                alocacaoService, produtoService, loteService);
    }
}
