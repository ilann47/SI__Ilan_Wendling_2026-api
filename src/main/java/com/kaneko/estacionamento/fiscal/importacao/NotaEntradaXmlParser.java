package com.kaneko.estacionamento.fiscal.importacao;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

@Component
public class NotaEntradaXmlParser {
    public static final int LIMITE_BYTES = 2 * 1024 * 1024;
    private static final String NS = "http://www.portalfiscal.inf.br/nfe";

    /** Leitura operacional restrita à NF-e 4.00, não valida assinatura nem autorização SEFAZ. */
    public DocumentoXmlEntrada ler(byte[] xml) {
        if (xml == null || xml.length == 0 || xml.length > LIMITE_BYTES)
            throw new IllegalArgumentException("Envie um XML de NF-e com até 2 MB.");
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", "64");
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override public void error(SAXParseException e) throws SAXParseException { throw e; }
                @Override public void fatalError(SAXParseException e) throws SAXParseException { throw e; }
            });
            var root = builder.parse(new ByteArrayInputStream(xml)).getDocumentElement();
            if (!NS.equals(root.getNamespaceURI()) || !List.of("NFe", "nfeProc").contains(root.getLocalName()))
                throw new IllegalArgumentException("O arquivo deve conter uma NF-e 4.00.");
            var nfe = "nfeProc".equals(root.getLocalName()) ? unico(root, "NFe") : root;
            var inf = unico(nfe, "infNFe");
            if (!"4.00".equals(inf.getAttribute("versao")))
                throw new IllegalArgumentException("Versão de XML não suportada. Utilize NF-e 4.00.");
            String identificador = inf.getAttribute("Id");
            if (!identificador.matches("NFe[0-9]{44}"))
                throw new IllegalArgumentException("Chave de acesso ausente ou inválida.");
            String chave = identificador.substring(3);
            validarChave(chave);
            var ide = unico(inf, "ide");
            if (!"1".equals(texto(ide, "tpNF", 1)) || !"1".equals(texto(ide, "finNFe", 1)))
                throw new IllegalArgumentException("Importe uma NF-e normal de saída do fornecedor. Devoluções, complementos e ajustes não pertencem a este fluxo de compra.");
            String numero = texto(ide, "nNF", 9), serie = texto(ide, "serie", 3), modelo = texto(ide, "mod", 2);
            var emissao = OffsetDateTime.parse(texto(ide, "dhEmi", 40)).toLocalDate();
            var emit = unico(inf, "emit");
            String emitente = texto(emit, "CNPJ", 14);
            String destinatario = texto(unico(inf, "dest"), "CNPJ", 14);
            if (!emitente.matches("[0-9]{14}") || !destinatario.matches("[0-9]{14}")
                    || !"55".equals(modelo) || !chave.substring(6, 20).equals(emitente)
                    || !chave.substring(20, 22).equals(modelo)
                    || Integer.parseInt(chave.substring(22, 25)) != Integer.parseInt(serie)
                    || Integer.parseInt(chave.substring(25, 34)) != Integer.parseInt(numero))
                throw new IllegalArgumentException("Chave de acesso incompatível com os dados da NF-e.");
            var itens = new ArrayList<DocumentoXmlEntrada.Item>();
            var numeros = new HashSet<Integer>();
            for (var det : filhos(inf, "det")) {
                int seq = Integer.parseInt(det.getAttribute("nItem"));
                if (seq < 1 || seq > 990 || !numeros.add(seq))
                    throw new IllegalArgumentException("Itens com sequência inválida ou repetida.");
                var prod = unico(det, "prod");
                if (!"1".equals(texto(prod, "indTot", 1)))
                    throw new IllegalArgumentException("Itens que não compõem o total da NF-e exigem um fluxo específico, fora desta importação de compra.");
                var qtd = decimal(prod, "qCom", true);
                var unitario = decimal(prod, "vUnCom", true);
                var bruto = dinheiro(prod, "vProd", true);
                var desconto = dinheiro(prod, "vDesc", false);
                if (qtd.signum() <= 0 || desconto.compareTo(bruto) > 0
                        || qtd.multiply(unitario).setScale(2, RoundingMode.HALF_UP).compareTo(bruto) != 0)
                    throw new IllegalArgumentException("Quantidade, preço ou desconto inválido no item " + seq + ".");
                itens.add(new DocumentoXmlEntrada.Item(seq, texto(prod, "cProd", 60),
                        texto(prod, "xProd", 120), texto(prod, "uCom", 6), qtd, unitario, bruto, desconto));
            }
            if (itens.isEmpty() || itens.size() > 990)
                throw new IllegalArgumentException("A NF-e deve conter entre 1 e 990 itens.");
            var total = unico(unico(inf, "total"), "ICMSTot");
            var somaProdutos = itens.stream().map(DocumentoXmlEntrada.Item::valorProdutos).reduce(BigDecimal.ZERO, BigDecimal::add);
            var somaDescontos = itens.stream().map(DocumentoXmlEntrada.Item::desconto).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (somaProdutos.compareTo(dinheiro(total, "vProd", true)) != 0
                    || somaDescontos.compareTo(dinheiro(total, "vDesc", false)) != 0)
                throw new IllegalArgumentException("Totais de produtos/descontos divergem dos itens do XML.");
            return new DocumentoXmlEntrada(chave, numero, serie, modelo, emitente,
                    texto(emit, "xNome", 120), destinatario, emissao, dinheiro(total, "vFrete", false),
                    dinheiro(total, "vSeg", false), dinheiro(total, "vOutro", false),
                    dinheiro(total, "vNF", true), List.copyOf(itens));
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) {
            throw new IllegalArgumentException("XML inválido, estrutura não suportada ou conteúdo externo proibido.");
        }
    }

    private static List<Element> filhos(Element pai, String nome) {
        var resultado = new ArrayList<Element>();
        for (Node n = pai.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element e && nome.equals(e.getLocalName()) && NS.equals(e.getNamespaceURI())) resultado.add(e);
        return resultado;
    }
    private static Element unico(Element pai, String nome) {
        var elementos = filhos(pai, nome);
        if (elementos.size() != 1) throw new IllegalArgumentException("Campo XML ausente ou repetido: " + nome + ".");
        return elementos.getFirst();
    }
    private static String texto(Element pai, String nome, int limite) {
        var element = unico(pai, nome);
        for (Node n = element.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element) throw new IllegalArgumentException("Estrutura inválida em " + nome + ".");
        String valor = element.getTextContent().trim();
        if (valor.isBlank() || valor.length() > limite) throw new IllegalArgumentException("Valor inválido em " + nome + ".");
        return valor;
    }
    private static BigDecimal decimal(Element pai, String nome, boolean obrigatorio) {
        if (!obrigatorio && filhos(pai, nome).isEmpty()) return BigDecimal.ZERO;
        String valor = texto(pai, nome, 30);
        if (!valor.matches("[0-9]{1,15}(\\.[0-9]{1,10})?")) throw new IllegalArgumentException("Valor numérico inválido em " + nome + ".");
        return new BigDecimal(valor);
    }
    private static BigDecimal dinheiro(Element pai, String nome, boolean obrigatorio) {
        var valor = decimal(pai, nome, obrigatorio);
        try {
            var exato = valor.setScale(2, RoundingMode.UNNECESSARY);
            if (exato.precision() > 15) throw new ArithmeticException();
            return exato;
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Precisão monetária não suportada em " + nome + ".");
        }
    }
    private static void validarChave(String chave) {
        int soma = 0, peso = 2;
        for (int i = 42; i >= 0; i--) { soma += (chave.charAt(i) - '0') * peso; peso = peso == 9 ? 2 : peso + 1; }
        int digito = 11 - soma % 11;
        if ((digito >= 10 ? 0 : digito) != chave.charAt(43) - '0')
            throw new IllegalArgumentException("Dígito verificador da chave de acesso inválido.");
    }
}
