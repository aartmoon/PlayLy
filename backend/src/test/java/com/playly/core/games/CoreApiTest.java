package com.playly.core.games;

import com.playly.core.web.Pages;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CoreApiTest {
    @Test
    void publicCatalogReturnsAContractPage() throws Exception {
        var games = mock(GameService.class);
        when(games.catalog(null, null, 20)).thenReturn(new Pages.Page<>(List.of(), null));

        MockMvcBuilders.standaloneSetup(new GameApi(games)).build()
                .perform(get("/api/v1/catalog/games"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"items":[],"nextCursor":null}
                        """));
    }
}
