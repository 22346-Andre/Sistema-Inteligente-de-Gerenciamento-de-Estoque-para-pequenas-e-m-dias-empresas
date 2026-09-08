package com.smartstock.backend.controller;

import com.smartstock.backend.service.ImportacaoService;
import com.smartstock.backend.service.NotaFiscalDuplicadaException;
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


    @PostMapping(value = "/produtos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importarEmLote(
            @RequestParam(value = "ficheiro", required = false) MultipartFile ficheiro,
            @RequestParam(value = "file", required = false) MultipartFile file) {

        MultipartFile arquivoParaProcessar = (ficheiro != null) ? ficheiro : file;

        if (arquivoParaProcessar == null || arquivoParaProcessar.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Erro: Nenhum ficheiro foi enviado ou o ficheiro está vazio.");
        }

        try {
            String relatorio = importacaoService.processarFicheiro(arquivoParaProcessar);
            return ResponseEntity.ok(relatorio);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro interno ao processar o CSV: " + e.getMessage());
        }
    }

    @PostMapping(value = "/xml-direto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importarXmlDireto(
            @RequestParam(value = "ficheiro", required = false) MultipartFile ficheiro,
            @RequestParam(value = "file", required = false) MultipartFile file,
            // 🆕 Toda NF-e é uma compra de verdade — pergunta como foi paga.
            // pagamentoImediato=true (padrão se omitido) -> baixa o Caixa agora.
            // false -> cria uma Despesa a pagar, com o vencimento informado.
            @RequestParam(value = "pagamentoImediato", required = false) Boolean pagamentoImediato,
            @RequestParam(value = "dataVencimento", required = false) String dataVencimento) {

        MultipartFile arquivoParaProcessar = ficheiro != null ? ficheiro : file;

        if (arquivoParaProcessar == null || arquivoParaProcessar.isEmpty() || !arquivoParaProcessar.getOriginalFilename().toLowerCase().endsWith(".xml")) {
            return ResponseEntity.badRequest().body("Erro: Envie um arquivo XML válido.");
        }

        try {

            Map<String, Object> dadosExtraidos = importacaoService.extrairDadosDoXmlSefaz(arquivoParaProcessar);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> produtosEncontrados = (List<Map<String, Object>>) dadosExtraidos.get("produtos");

            if (produtosEncontrados == null || produtosEncontrados.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Nenhum produto pôde ser extraído deste XML.");
            }

            java.time.LocalDate vencimento = (dataVencimento != null && !dataVencimento.isBlank())
                    ? java.time.LocalDate.parse(dataVencimento)
                    : null;

            String relatorio = importacaoService.salvarProdutosLidos(dadosExtraidos, pagamentoImediato, vencimento);

            return ResponseEntity.ok(relatorio);
        } catch (NotaFiscalDuplicadaException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro interno ao processar o XML: " + e.getMessage());
        }
    }
}