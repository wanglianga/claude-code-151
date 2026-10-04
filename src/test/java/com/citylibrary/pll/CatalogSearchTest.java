package com.citylibrary.pll;

import com.citylibrary.pll.dto.CatalogView;
import com.citylibrary.pll.service.CatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 搜索展示：本馆库存、跨馆库存、排队、预计到馆、电子资源、远端馆保护。 */
class CatalogSearchTest extends AbstractIntegrationTest {

    @Autowired
    private CatalogService catalogService;

    @Test
    void search_showsLocalCrossLibraryQueueAndEbook() {
        List<CatalogView> views = catalogService.search("三体", "CENTRAL");
        assertEquals(1, views.size());
        CatalogView v = views.get(0);
        assertEquals(1, v.localAvailable());
        assertTrue(v.ebookAvailable());
        assertNotNull(v.ebookUrl());
        assertEquals(0, v.queueLength());
        assertEquals(0, v.minEtaDays());
        // 城东 4 本、城西 2 本均可作为跨馆来源（远端北峰孤本被保护，不出现）
        assertTrue(v.sources().stream().anyMatch(s -> s.libraryCode().equals("EAST") && s.available() == 4));
        assertTrue(v.sources().stream().anyMatch(s -> s.libraryCode().equals("WEST") && s.available() == 2));
        assertTrue(v.sources().stream().noneMatch(s -> s.libraryCode().equals("NORTH")));
    }

    @Test
    void search_fromRemoteBranch_seesLocalFirst() {
        CatalogView v = catalogService.search("三体", "NORTH").get(0);
        assertEquals(1, v.localAvailable());
        assertEquals(0, v.minEtaDays());
    }

    @Test
    void retention_ratio_protectsSoleCopies() {
        // 《围城》：城东与北峰各仅 1 本，均触发留存比例（普通 25%/远端 50%），中心馆无可调来源
        CatalogView v = catalogService.search("围城", "CENTRAL").get(0);
        assertEquals(0, v.localAvailable());
        assertNull(v.minEtaDays());
        assertTrue(v.sources().isEmpty());
    }
}
