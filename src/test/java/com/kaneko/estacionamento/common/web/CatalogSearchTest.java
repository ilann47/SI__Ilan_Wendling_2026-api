package com.kaneko.estacionamento.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CatalogSearchTest {
    @Test void normalizaAcentosMaiusculosIndependenteDoLocaleDoBanco() {
        var cb = mock(CriteriaBuilder.class);
        Root<Object> root = mock(Root.class);
        CatalogSearch.<Object>porNome("  ÁREA  ").toPredicate(root, null, cb);
        var strings = ArgumentCaptor.forClass(String.class);
        verify(cb, times(2)).literal(strings.capture());
        String source = strings.getAllValues().get(0);
        String target = strings.getAllValues().get(1);
        assertThat(source.length()).isEqualTo(target.length());
        assertThat(source).contains("Á", "É", "Ç", "á", "é", "ç");
        for (String text : new String[] { "Á", "á", "Ã", "ã", "Ç", "ç" }) {
            int index = source.indexOf(text);
            assertThat(target.charAt(index)).isEqualTo(text.equals("Ç") || text.equals("ç") ? 'c' : 'a');
        }
        verify(cb).locate(any(), eq("area"));
    }
}
