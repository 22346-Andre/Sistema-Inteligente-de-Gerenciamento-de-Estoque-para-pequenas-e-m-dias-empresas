# 📦 SmartStock - Sistema Inteligente de Gestão de Estoque e ERP Fiscal

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![React](https://img.shields.io/badge/React-20232A?style=for-the-badge&logo=react&logoColor=61DAFB)
![TypeScript](https://img.shields.io/badge/TypeScript-007ACC?style=for-the-badge&logo=typescript&logoColor=white)
![MySQL](https://img.shields.io/badge/mysql-4479A1.svg?style=for-the-badge&logo=mysql&logoColor=white)

O **SmartStock** é um sistema de Planeamento de Recursos Empresariais (ERP) focado no controlo de estoque para Pequenas e Médias Empresas (PMEs). O sistema vai além do controlo básico de "entradas e saídas", introduzindo inteligência fiscal, gestão rigorosa de validade de lotes (algoritmo FEFO) e arquitetura SaaS multi-tenant.

Este projeto foi desenvolvido com foco na resolução de problemas reais de gestão e limitações de infraestrutura, garantindo alta performance, resiliência e aderência às normas tributárias brasileiras (SEFAZ/CONFAZ).

---

## ✨ Principais Funcionalidades e Diferenciais

* 🏢 **Arquitetura SaaS Multi-Tenant:** Base de dados unificada com isolamento lógico de informações por empresa (`empresaId`), permitindo que múltiplos lojistas utilizem a plataforma de forma segura e independente.
* 🧠 **Inteligência Fiscal e Tributária:**
  * Cálculo dinâmico e automático de **CFOP** com base na operação (Entrada, Saída, Quebra/Perda, Substituição Tributária e Localização).
  * Gestão detalhada de NCM e Impostos associados (ICMS, IPI, PIS, COFINS com as suas respetivas alíquotas e esferas).
* 📦 **Controlo de Lotes por FEFO (*First Expire, First Out*):** As saídas de estoque abatem automaticamente as quantidades dos lotes com data de validade mais próxima, mitigando prejuízos por perecimento.
* 📊 **Relatórios Contábeis em PDF:** Geração de documentos fiscais prontos para auditoria (Livro de Inventário Fiscal, Relatório de Movimentações e Relatório Analítico de Quebras e Perdas) utilizando a biblioteca iText.
* 🔌 **Integração Externa via Webhooks:** Recebimento de payloads de plataformas de e-commerce (ex: Shopee, Mercado Livre) para baixa automática de estoque em tempo real.
* 📈 **Dashboard Estratégico:** Análise financeira com cálculo de Custo Médio Ponderado, Valor Imobilizado, Margem de Lucro e categorização automática pela **Curva ABC**.
* 🔐 **Segurança Avançada:** Autenticação via JWT (JSON Web Tokens) e integração de Single Sign-On (SSO) com **Google OAuth2** geridos pelo Spring Security.
* ♿ **Acessibilidade:** Suporte integrado ao VLibras no front-end para inclusão de utilizadores surdos.

---

## 💻 Tecnologias Utilizadas

### Backend
* **Java 17+**
* **Spring Boot 3** (Web, Data JPA, Security, Validation)
* **MySQL** (Base de dados relacional robusta para armazenamento de dados da aplicação)
* **OpenPDF / iText** (Geração de relatórios PDF complexos)
* **Swagger / OpenAPI** (Documentação automática da API)
* **Lombok** (Redução de código repetitivo)

### Frontend
* **React 18** com **TypeScript**
* **Tailwind CSS** e **Shadcn/ui** (Componentização de UI moderna e responsiva)
* **Axios** (Integração assíncrona de APIs)
* **Recharts** (Visualização de dados)
* **Date-fns** (Manipulação de datas)

---

## 🏛️ Arquitetura e Lógica de Negócio

### Motor de Decisão CFOP (`CfopService`)
O sistema isenta o operador de decisões contábeis complexas. Ao registar uma operação, o backend avalia as *flags* do produto e da transação e atribui o CFOP correspondente:
* `1.102` / `1.403`: Entradas com ou sem ST.
* `5.102` / `5.405`: Vendas internas com ou sem ST.
* `5.927`: Código específico ativado automaticamente para baixas de estoque por avaria ou validade, direcionando o custo para o relatório de perdas financeiras.

### Fluxo do Algoritmo FEFO (`ProdutoService`)
```text
1. Requisição de Venda (Qtd: 10 unidades)
2. Busca de todos os lotes do Produto ID ordenados por data de validade (ASC).
3. Loop pelos lotes a iterar o abatimento:
   - Se Lote A tem 5 unidades: Abate 5, Lote A zera, restam 5.
   - Vai para Lote B. Se Lote B tem 20 unidades: Abate 5, Lote B fica com 15, Venda concluída.
4. Recálculo automático do Custo Médio Ponderado da mercadoria restante.
