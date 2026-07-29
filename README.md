# 📦 SmartStock - Sistema Inteligente de Gestão de Estoque e ERP Fiscal

![Java](https://img.shields.io/badge/Java_25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.5-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![MySQL](https://img.shields.io/badge/mysql-4479A1.svg?style=for-the-badge&logo=mysql&logoColor=white)
![Flyway](https://img.shields.io/badge/Flyway-CC0200?style=for-the-badge&logo=flyway&logoColor=white)
![Render](https://img.shields.io/badge/Render-46E3B7?style=for-the-badge&logo=render&logoColor=white)

O **SmartStock** é um sistema de Planejamento de Recursos Empresariais (ERP) focado no controle de estoque para Pequenas e Médias Empresas (PMEs) brasileiras. Vai além do controle básico de "entradas e saídas", com inteligência fiscal, gestão de validade de lotes, sugestão de compras por lógica fuzzy, Curva ABC por demanda, cobrança via PIX/WhatsApp e arquitetura SaaS multi-tenant.

🔗 **API em produção:** `https://smartstock-backend-j7em.onrender.com`
🔗 **Frontend:** [github.com/22346-Andre/frontendrepository](https://github.com/22346-Andre/frontendrepository)

---

## ✨ Principais Funcionalidades e Diferenciais

### Inteligência de estoque e compras
* 🧠 **Sugestões de Compra por Lógica Fuzzy:** cálculo de urgência de reposição combinando estoque atual, velocidade de venda recente e prazo de entrega de cada fornecedor.
* 📈 **Curva ABC por Demanda:** classificação de produtos em Classe A/B/C configurável por **faturamento**, **lucratividade** ou **giro** de vendas em um período — não pelo capital parado em estoque (evita classificar como "importante" um produto caro e encalhado).
* ❄️ **Painel de Estoque Morto ("Dinheiro Congelado"):** identifica produtos parados há N dias sem venda e sugere lista de liquidação com desconto.
* 📦 **Controle de Lotes (FEFO — *First Expire, First Out*):** rastreamento de lotes por validade, priorizando a saída dos itens que vencem primeiro.

### Fiscal e financeiro
* 🧾 **Motor de CFOP:** cálculo dinâmico e automático do CFOP com base na operação (entrada, saída, quebra/perda, substituição tributária).
* 📄 **Relatórios em PDF:** geração de cupom fiscal (bobina) e DANFE simplificados via iText — documentos organizacionais, sem valor fiscal real.
* 💳 **Cobrança via PIX:** geração local do código "Copia e Cola" (padrão BR Code / EMV do Banco Central), sem depender de nenhum gateway de pagamento externo.
* 💬 **Cobrança e recibo via WhatsApp:** links `wa.me` prontos, gerados automaticamente para contas a receber e recibos de venda.

### Arquitetura e segurança
* 🏢 **SaaS Multi-Tenant:** base de dados unificada com isolamento lógico por empresa (`empresaId`), extraído do token JWT — nunca aceito como parâmetro livre do cliente.
* 🔐 **Autenticação JWT (RS256)** com Spring Security, login social via **Google OAuth2**, recuperação de senha por e-mail (token de uso único, validade de 1h) e **rate limiting** contra força bruta no login (bloqueio após 5 tentativas falhas).
* 🔒 **Concorrência segura:** baixa de estoque protegida por bloqueio pessimista (`SELECT ... FOR UPDATE`), evitando estoque negativo em vendas simultâneas do mesmo produto.
* 🗄️ **Migrações versionadas com Flyway:** todo o histórico de alterações estruturais do banco é rastreável e reproduzível.
* ✉️ **E-mail transacional via API HTTP (Brevo):** evita a instabilidade/bloqueio de portas SMTP comum em plataformas de hospedagem.
* 🔌 **Webhooks:** recebimento de payloads de plataformas de e-commerce para baixa automática de estoque, validados por assinatura secreta exclusiva por empresa.

---

## 💻 Tecnologias Utilizadas

### Backend
* **Java 25**
* **Spring Boot 3.5** (Web, Data JPA, Security, Validation, Mail, Async, Scheduling)
* **Spring Security + OAuth2 Resource Server** — autenticação stateless via JWT (par de chaves RSA)
* **MySQL 8** — banco relacional gerenciado (Aiven Cloud)
* **Flyway** — controle de versão do schema do banco
* **Hibernate / Spring Data JPA** — ORM e camada de persistência
* **iText** — geração de relatórios em PDF
* **Lombok** — redução de código repetitivo
* **springdoc-openapi (Swagger)** — documentação automática da API
* **Maven** — build e gerenciamento de dependências

### Integrações externas
* **Brevo** — envio de e-mail transacional via API HTTPS
* **Google OAuth2** — login social
* **BrasilAPI / ReceitaWS** — consulta de dados de CNPJ

---

## 🏛️ Arquitetura e Lógica de Negócio

O projeto segue arquitetura em camadas: `Controller` (endpoints REST) → `Service` (regras de negócio) → `Repository` (acesso a dados via Spring Data JPA) → `Model` (entidades JPA), com `DTOs` moldando o contrato de entrada/saída da API e um tratador de exceções global (`TratadorDeErros`) padronizando toda resposta de erro.

### Motor de Decisão CFOP (`CfopService`)
O sistema isenta o operador de decisões contábeis complexas. Ao registrar uma operação, o backend avalia as *flags* do produto e da transação e atribui o CFOP correspondente:
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
```

### Curva ABC por Demanda (`CurvaAbcService`)
Classifica produtos ordenando-os pelo critério escolhido (faturamento, lucratividade ou volume de vendas) no período, acumulando o percentual até os cortes de 80% (Classe A) e 95% (Classe B) — produtos empatados no mesmo valor são agrupados no mesmo bloco, evitando que a ordem de desempate os separe em classes diferentes.

### Isolamento Multi-Tenant
Toda entidade de negócio referencia uma `Empresa`. O `empresaId` vem exclusivamente do claim do token JWT (nunca de parâmetro de URL/body), e toda consulta e operação de escrita nos serviços filtra e valida explicitamente esse vínculo antes de prosseguir, prevenindo acesso cruzado entre empresas.

---

## 🚀 Rodando o projeto localmente

### Pré-requisitos
* JDK 25
* Maven 3.9+
* Uma instância MySQL 8 acessível

### Configuração

Defina as seguintes variáveis de ambiente (ou um arquivo `.env` / `application-local.properties`, conforme seu setup):

| Variável | Finalidade |
|---|---|
| `DB_PASSWORD` | Senha de conexão com o banco MySQL |
| `ADMIN_PASSWORD` | Senha do usuário administrador inicial, criado automaticamente no primeiro boot |
| `RSA_PRIVATE_KEY_PATH` / `RSA_PUBLIC_KEY_PATH` | Par de chaves usado para assinar e validar os tokens JWT |
| `BREVO_API_KEY` | Chave da API de e-mail transacional (Brevo) |
| `BREVO_SENDER_EMAIL` | E-mail remetente verificado na Brevo |
| `GOOGLE_CLIENT_ID` | Client ID do OAuth do Google (login social) |
| `FRONTEND_URL` | URL do frontend, usada para montar links de e-mail (ex.: recuperação de senha) |

### Passo a passo

```bash
# 1. Clone o repositório
git clone https://github.com/22346-Andre/Sistema-Inteligente-de-Gerenciamento-de-Estoque-para-pequenas-e-m-dias-empresas.git
cd Sistema-Inteligente-de-Gerenciamento-de-Estoque-para-pequenas-e-m-dias-empresas/backend/backend

# 2. Configure as variáveis de ambiente (ver tabela acima)

# 3. Rode a aplicação
./mvnw spring-boot:run
```

A API sobe por padrão em `http://localhost:8080`. As migrações Flyway são aplicadas automaticamente na inicialização.

### Documentação interativa da API

Com a aplicação rodando, a documentação Swagger fica disponível em `http://localhost:8080/swagger-ui/index.html`.

---

## 📁 Estrutura do Projeto

```
src/main/java/com/smartstock/backend/
├── controller/      # Endpoints REST
├── service/         # Regras de negócio
├── repository/       # Interfaces de acesso a dados (Spring Data JPA)
├── model/            # Entidades JPA
├── dto/               # Objetos de transporte (entrada/saída da API)
├── exception/         # Exceções de negócio + tratador global de erros
├── infra/security/    # Configuração de autenticação e autorização
├── specification/     # Filtros de busca dinâmica
└── config/            # Configurações gerais (agendador, execução assíncrona, etc.)

src/main/resources/
├── application.properties
└── db/migration/       # Scripts de migração Flyway (Vx__descricao.sql)
```

---

## 🗄️ Principais Endpoints

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/auth/registrar-empresa` | Cadastro de nova empresa + usuário administrador |
| `POST` | `/auth/login` | Login (e-mail/senha) |
| `POST` | `/auth/login/google` | Login social via Google |
| `POST` | `/auth/esqueci-senha` / `/auth/redefinir-senha` | Fluxo de recuperação de senha |
| `GET` | `/produtos/paginado` | Listagem paginada, com busca e filtro por categoria |
| `POST` | `/produtos/{id}/saida` | Registra venda/perda, com baixa de estoque protegida contra concorrência |
| `GET` | `/estatisticas/curva-abc` | Curva ABC por critério (faturamento/lucratividade/giro) |
| `GET` | `/sugestoes-compra` | Lista de reposição sugerida, com urgência calculada |
| `POST` | `/pix/gerar` | Gera cobrança PIX (Copia e Cola) |
| `GET` | `/fiados/{id}/whatsapp` | Gera link de cobrança via WhatsApp |

---

## 📄 Licença

Projeto acadêmico/comercial privado. Todos os direitos reservados.
