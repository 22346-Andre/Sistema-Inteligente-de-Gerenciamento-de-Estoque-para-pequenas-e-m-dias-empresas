package com.smartstock.backend.model;

import jakarta.persistence.Embeddable;
import lombok.Data;
import java.math.BigDecimal;

@Embeddable
@Data
public class Imposto {


    private String sigla;
    private String esfera;
    private BigDecimal aliquota;
}