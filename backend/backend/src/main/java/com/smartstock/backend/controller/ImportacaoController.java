package com.smartstock.backend.controller;

import com.smartstock.backend.service.ImportacaoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType; // Importação necessária
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/importacao")
public class ImportacaoController {

    private final ImportacaoService importacaoService;

    public ImportacaoController(ImportacaoService importacaoService) {
        this.importacaoService = importacaoService;
    }


    @PostMapping(value = "/lote", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> importarEmLote(@RequestParam("ficheiro") MultipartFile ficheiro) {
        try {
            if (ficheiro.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("O ficheiro está vazio.");
            }

            String relatorio = importacaoService.processarFicheiro(ficheiro);

            return ResponseEntity.ok(relatorio);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao processar o ficheiro: " + e.getMessage());
        }
    }
}