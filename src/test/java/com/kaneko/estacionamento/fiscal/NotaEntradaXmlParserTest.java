package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.fiscal.importacao.NotaEntradaXmlParser;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NotaEntradaXmlParserTest {
    private final NotaEntradaXmlParser parser = new NotaEntradaXmlParser();

    @Test void leDadosOperacionaisSemInferirAutorizacaoFiscal() {
        var nota = parser.ler(xml("11222333000181").getBytes(StandardCharsets.UTF_8));
        assertThat(nota.numero()).isEqualTo("123");
        assertThat(nota.destinatarioDocumento()).isEqualTo("11222333000181");
        assertThat(nota.total()).isEqualByComparingTo("20.00");
        assertThat(nota.itens()).hasSize(1);
        assertThat(nota.itens().getFirst().quantidade()).isEqualByComparingTo("2");
    }
    @Test void rejeitaDtdEntidadeExternaEstruturaInvalidaETamanho() {
        assertThatThrownBy(() -> parser.ler(("<!DOCTYPE NFe [<!ENTITY xxe SYSTEM 'http://127.0.0.1:9/'>]>"
                + xml("11222333000181")).getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.ler("<html/>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.ler(new byte[NotaEntradaXmlParser.LIMITE_BYTES + 1]))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejeitaChaveInconsistenteEItensDuplicados() {
        assertThatThrownBy(() -> parser.ler(xml("11222333000181").replace("Id=\"NFe", "Id=\"XYZ")
                .getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
        String doc = xml("11222333000181");
        String item = doc.substring(doc.indexOf("<det"), doc.indexOf("</det>") + 6);
        assertThatThrownBy(() -> parser.ler(doc.replace("</det>", "</det>" + item)
                .getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejeitaDevolucaoComplementoEOperacaoDeEntradaDoEmitente() {
        for (String finalidade : new String[]{"2", "3", "4", "5", "6"})
            assertThatThrownBy(() -> parser.ler(xml("11222333000181")
                    .replace("<finNFe>1</finNFe>", "<finNFe>" + finalidade + "</finNFe>")
                    .getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.ler(xml("11222333000181")
                .replace("<tpNF>1</tpNF>", "<tpNF>0</tpNF>").getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejeitaItemForaDoTotalENaoArredondaDinheiroSilenciosamente() {
        assertThatThrownBy(() -> parser.ler(xml("11222333000181")
                .replace("<indTot>1</indTot>", "<indTot>0</indTot>").getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.ler(xml("11222333000181")
                .replace("<vNF>20.00</vNF>", "<vNF>20.001</vNF>").getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }
    public static String xml(String destinatario) {
        String base = "4126091122233300018155001000000123100000001";
        int sum = 0, weight = 2;
        for (int i = 42; i >= 0; i--) { sum += (base.charAt(i) - '0') * weight; weight = weight == 9 ? 2 : weight + 1; }
        int digit = 11 - sum % 11;
        String chave = base + (digit >= 10 ? 0 : digit);
        return """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe"><infNFe versao="4.00" Id="NFe%s">
                <ide><mod>55</mod><serie>1</serie><nNF>123</nNF><dhEmi>2026-09-12T10:00:00-03:00</dhEmi><tpNF>1</tpNF><finNFe>1</finNFe></ide>
                <emit><CNPJ>11222333000181</CNPJ><xNome>Fornecedor XML</xNome></emit>
                <dest><CNPJ>%s</CNPJ></dest>
                <det nItem="1"><prod><cProd>A1</cProd><xProd>Produto importado</xProd><uCom>UN</uCom>
                <qCom>2</qCom><vUnCom>10.00</vUnCom><vProd>20.00</vProd><vDesc>0</vDesc><indTot>1</indTot></prod></det>
                <total><ICMSTot><vProd>20.00</vProd><vDesc>0</vDesc><vFrete>0</vFrete><vSeg>0</vSeg><vOutro>0</vOutro><vNF>20.00</vNF></ICMSTot></total>
                </infNFe></NFe>
                """.formatted(chave, destinatario);
    }
}
