package com.smartstock.backend.controller;

import com.smartstock.backend.service.RelatorioPdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/relatorios")
public class RelatorioController {

    private final RelatorioPdfService relatorioPdfService;

    public RelatorioController(RelatorioPdfService relatorioPdfService) {
        this.relatorioPdfService = relatorioPdfService;
    }

    @GetMapping("/balanco/pdf")
    public ResponseEntity<byte[]> descarregarBalancoPdf() {
        // o método não precisa receber o nome, ele usa o JWT do usuário logado!
        byte[] pdfBytes = relatorioPdfService.gerarBalancoGeralPdf();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "balanco_estoque_smartstock.pdf");

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    @GetMapping("/movimentacoes/pdf")
    public ResponseEntity<byte[]> descarregarMovimentacoesPdf() {
        byte[] pdfBytes = relatorioPdfService.gerarRelatorioMovimentacoesPdf();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "historico_movimentacoes_smartstock.pdf");

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    @GetMapping("/inventario/pdf")
    public ResponseEntity<byte[]> descarregarInventarioPdf() {
        byte[] pdfBytes = relatorioPdfService.gerarRelatorioInventarioFiscalPdf();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "inventario_fiscal_smartstock.pdf");

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }


    @GetMapping("/perdas/pdf")
    public ResponseEntity<byte[]> descarregarPerdasPdf() {
        byte[] pdfBytes = relatorioPdfService.gerarRelatorioPerdasPdf();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "relatorio_perdas_smartstock.pdf");

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }
}