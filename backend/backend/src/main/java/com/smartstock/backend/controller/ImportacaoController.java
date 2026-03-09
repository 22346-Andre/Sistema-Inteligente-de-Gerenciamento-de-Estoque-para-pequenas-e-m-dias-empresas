package com.smartstock.backend.controller;

import com.smartstock.backend.service.ImportacaoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/importacao")
public class ImportacaoController {

    private final ImportacaoService importacaoService;

    public ImportacaoController(ImportacaoService importacaoService) {
        this.importacaoService = importacaoService;
    }

    //ROTA 1: Planilhas CSV (Cadastros em massa/Catálogo)
    @PostMapping(value = "/produtos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> importarEmLote(@RequestParam(value = "ficheiro", required = false) MultipartFile ficheiro,
                                                 @RequestParam(value = "file", required = false) MultipartFile file) {
        MultipartFile arquivoParaProcessar = ficheiro != null ? ficheiro : file;

        try {
            if (arquivoParaProcessar == null || arquivoParaProcessar.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("O ficheiro está vazio ou não foi enviado corretamente.");
            }
            String relatorio = importacaoService.processarFicheiro(arquivoParaProcessar);
            return ResponseEntity.ok(relatorio);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao processar o arquivo CSV: " + e.getMessage());
        }
    }

    // ROTA 2: EXTRAÇÃO VIA XML OFICIAL (SEFAZ/NFe)
    @PostMapping("/processar-xml")
    public ResponseEntity<?> processarXmlNotaFiscal(@RequestParam(value = "file", required = false) MultipartFile file,
                                                    @RequestParam(value = "ficheiro", required = false) MultipartFile ficheiro) {
        MultipartFile arquivoParaProcessar = file != null ? file : ficheiro;

        if (arquivoParaProcessar == null || arquivoParaProcessar.isEmpty() || !arquivoParaProcessar.getOriginalFilename().toLowerCase().endsWith(".xml")) {
            return ResponseEntity.badRequest().body("Erro: Envie um arquivo XML válido.");
        }
        try {
            List<Map<String, Object>> produtosEncontrados = importacaoService.extrairProdutosDoXmlSefaz(arquivoParaProcessar);
            return ResponseEntity.ok(produtosEncontrados);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao processar o XML da NF-e: " + e.getMessage());
        }
    }

    // ROTA 3: SALVAR NF-e NO BANCO DE DADOS
    @PostMapping("/salvar")
    public ResponseEntity<?> salvarProdutosImportados(@RequestBody List<Map<String, Object>> produtosLidos) {
        try {
            if (produtosLidos == null || produtosLidos.isEmpty()) {
                return ResponseEntity.badRequest().body("Nenhum produto encontrado para salvar.");
            }
            importacaoService.salvarProdutosLidos(produtosLidos);
            return ResponseEntity.ok("Produtos salvos no estoque com sucesso!");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao salvar no banco de dados: " + e.getMessage());
        }
    }
}