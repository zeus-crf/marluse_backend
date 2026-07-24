# Fornecedores em Produtos (N:N) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir associar vários fornecedores a um produto, digitando os nomes direto no modal de cadastro/edição do produto — criando o fornecedor na hora quando o nome ainda não existir.

**Architecture:** Fornecedor vira uma entidade própria mínima (`id`, `nome` único, `ativo`) num pacote novo `fornecedores`, espelhando a estrutura de `clientes`. O vínculo com produto é um `@ManyToMany` puro (tabela `produto_fornecedores`, sem colunas extras) — deliberadamente **sem preço por fornecedor**, para não criar uma segunda fonte de verdade ao lado de `Produto.valorCompra`, que alimenta o cálculo de custo/lucro em vendas. As DTOs trafegam **nomes**, não ids: o frontend nunca manipula id de fornecedor, o que torna o "criar-na-hora" trivial. Toda a normalização (trim, dedup ignorando caixa, buscar-ou-criar) fica concentrada num único método, `FornecedorService.resolverPorNomes`, que é o único ponto de contato do `ProdutoService` com fornecedores.

**Tech Stack:** Backend Spring Boot 3 / Java 17 / JPA / Lombok (MySQL em dev via `ddl-auto: update`, Flyway apenas no perfil docker) / JUnit 5 + `@SpringBootTest` com H2 (`profile=test`). Frontend Angular 22 standalone + PrimeNG 21 + Tailwind, testes com Vitest (`@angular/build:unit-test`).

**Spec:** `marluse-frontend/docs/superpowers/specs/2026-07-24-fornecedor-produto-design.md`

**Decisões travadas (confirmadas com o usuário):**
- N:N (vários fornecedores por produto), **sem** preço por fornecedor no vínculo.
- Fornecedor guarda **apenas o nome** (mais `ativo` para soft-delete futuro).
- Campo **opcional**, sem backfill: produtos existentes ficam sem fornecedor.
- Aparece **somente** no modal de cadastro/edição de produto.
- UX: multi-select com chips e criação por digitação.
- **Sem** tela de gestão de fornecedores nesta entrega.

**Fora de escopo (não implementar):** tela/CRUD de fornecedores, renomear/excluir fornecedor, coluna de fornecedor na listagem de estoque, filtro por fornecedor, dados de contato (telefone/CNPJ/e-mail).

**Limitação conhecida (aceita, não tratar):** duas requisições simultâneas criando o mesmo fornecedor novo podem passar as duas pelo lookup antes de qualquer insert; a segunda viola a `UNIQUE` e aquele salvamento de produto falha. O operador tenta de novo e funciona. **Não implemente tratamento de corrida** — capturar `DataIntegrityViolationException` e refazer o lookup no `catch` *não funciona*, porque depois de um flush falhar o Hibernate marca a transação como rollback-only e o `EntityManager` fica inutilizável; a solução correta exigiria `REQUIRES_NEW`, que não se justifica para este volume de uso.

**Armadilha central deste plano:** `ProdutoResponse.from()` roda **fora de transação** hoje. Adicionar uma coleção `LAZY` a `Produto` sem `@EntityGraph` nos finders produz `LazyInitializationException` em runtime — e o teste com H2 pode não pegar isso se o teste estiver dentro de `@Transactional`. A Task 5 trata isso explicitamente.

---

## Convenções de path

- **Backend** (raiz `marluse/`): pacote base `src/main/java/com/example/marluse`, testes `src/test/java/com/example/marluse`, migrações `src/main/resources/db/migration`.
- **Frontend** (raiz `marluse-frontend/`): `src/app`.

Comandos de backend rodam a partir de `marluse/`. Comandos de frontend a partir de `marluse-frontend/`.

**Perfis do backend, e por que isso importa:**
- `test` → H2, `ddl-auto: create-drop`, **Flyway desligado**. As tabelas nascem das anotações JPA.
- dev (default) → MySQL, `ddl-auto: update`, **Flyway desligado**. As tabelas também nascem das anotações.
- `docker` → Flyway ligado. **A migration `V11` só é exercida aqui e em produção.**

Consequência prática: os testes **não** validam o SQL da migration. Escreva a migration com cuidado e confira que ela bate exatamente com o que o JPA gera.

**Segunda consequência, importante:** o H2 é *case-sensitive* em `UNIQUE`, o MySQL (collation `utf8mb4_..._ci`) é *case-insensitive*. Portanto a garantia de que `"Votorantim"` e `" votorantim "` viram um único fornecedor **tem que vir do código** (`findByNomeIgnoreCase` + dedup em `resolverPorNomes`), nunca da constraint do banco. A constraint é só a última linha de defesa.

---

## File Structure

**Backend — criar:**
- `src/main/java/com/example/marluse/fornecedores/model/Fornecedor.java` — entidade (nome, ativo).
- `src/main/java/com/example/marluse/fornecedores/repository/FornecedorRepository.java` — lookup por nome ignorando caixa + listagem ordenada.
- `src/main/java/com/example/marluse/fornecedores/dto/FornecedorResponse.java` — `(id, nome)`.
- `src/main/java/com/example/marluse/fornecedores/service/FornecedorService.java` — `listar()` e `resolverPorNomes()`.
- `src/main/java/com/example/marluse/fornecedores/controller/FornecedorController.java` — `GET /api/fornecedores`.
- `src/main/resources/db/migration/V11__fornecedores.sql` — as duas tabelas.
- `src/test/java/com/example/marluse/fornecedores/FornecedorServiceTest.java` — testes da normalização.

**Backend — modificar:**
- `estoque/model/Produto.java` — `@ManyToMany Set<Fornecedor> fornecedores`.
- `estoque/repository/ProdutoRepository.java` — `@EntityGraph` nos finders que alimentam `ProdutoResponse`.
- `estoque/dto/ProdutoRequest.java` — `List<String> fornecedores`.
- `estoque/dto/ProdutoAtualizarRequest.java` — `List<String> fornecedores`.
- `estoque/dto/ProdutoResponse.java` — `List<String> fornecedores` + mapeamento em `from()`.
- `estoque/service/ProdutoService.java` — resolve nomes no `criar` e no `atualizar`; `@Transactional` nesses dois métodos.
- `src/test/java/com/example/marluse/estoque/ProdutoServiceTest.java` — construtores dos records ganham 1 argumento; novos testes de vínculo.

**Frontend — criar:**
- `src/app/features/estoque/fornecedores.service.ts` — só `listar()`.
- `src/app/shared/components/multi-select-create/multi-select-create.component.ts` — CVA `string[]` sobre `p-autocomplete`.
- `src/app/shared/components/multi-select-create/multi-select-create.component.spec.ts` — teste do CVA.

**Frontend — modificar:**
- `src/app/features/estoque/models/estoque.models.ts` — `fornecedores` nas 3 interfaces + correção do typo `categorira`.
- `src/app/features/estoque/novo-produto-modal/novo-produto-modal.component.ts` — control, carga das opções, payload.
- `src/app/features/estoque/novo-produto-modal/novo-produto-modal.component.html` — o campo.

**Frontend — NÃO tocar:** `estoque.component.ts` / `.html` (o modal se vira sozinho), `vendas.models.ts`, `locacoes.models.ts` (o campo novo na resposta é aditivo e opcional para eles).

---

## Task 1: Baseline — garantir que a suíte compila e registrar o estado atual

Sem isso não há TDD: você não saberá se uma falha é sua ou pré-existente.

**Files:** nenhum (apenas execução)

- [ ] **Step 1: Compilar e rodar os testes de backend**

Run (em `marluse/`): `./mvnw test`

Expected: BUILD SUCCESS. Anote o total de testes.

Se falhar na **compilação** (construtores de record desatualizados em `ProdutoServiceTest` ou `PedidoServiceTest`), corrija os construtores para a assinatura atual **antes de seguir** e commite separadamente. Não comece a feature sobre uma suíte quebrada.

- [ ] **Step 2: Rodar os testes de frontend e registrar o baseline**

Run (em `marluse-frontend/`): `npm test -- --run`

Expected: pode haver falhas **pré-existentes** — vários `.spec.ts` gerados pelo CLI importam símbolos que não existem mais (ex.: `estoque.component.spec.ts` importa `Estoque`, mas a classe se chama `EstoqueComponent`). **Anote quais specs falham hoje.** O critério de aceite deste plano é: o teste novo passa e **nenhuma falha nova** aparece. Não conserte os specs legados — está fora de escopo.

- [ ] **Step 3: Commit (só se você precisou reparar a compilação no Step 1)**

```bash
git add src/test/java/com/example/marluse
git commit -m "test: corrige construtores desatualizados para a suite compilar"
```

---

## Task 2: Entidade Fornecedor + repository + migration

**Files:**
- Create: `marluse/src/main/java/com/example/marluse/fornecedores/model/Fornecedor.java`
- Create: `marluse/src/main/java/com/example/marluse/fornecedores/repository/FornecedorRepository.java`
- Create: `marluse/src/main/resources/db/migration/V11__fornecedores.sql`
- Create: `marluse/src/test/java/com/example/marluse/fornecedores/FornecedorServiceTest.java`

- [ ] **Step 1: Escrever o teste que falha**

Crie `marluse/src/test/java/com/example/marluse/fornecedores/FornecedorServiceTest.java`. Neste passo ele só exercita a entidade e o repository; os testes do service entram na Task 3.

```java
package com.example.marluse.fornecedores;

import com.example.marluse.fornecedores.model.Fornecedor;
import com.example.marluse.fornecedores.repository.FornecedorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class FornecedorServiceTest {

    @Autowired
    private FornecedorRepository fornecedorRepository;

    @BeforeEach
    void setUp() {
        fornecedorRepository.deleteAll();
    }

    @Test
    void deveEncontrarFornecedorIgnorandoCaixa() {
        fornecedorRepository.save(Fornecedor.builder().nome("Votorantim").build());

        Optional<Fornecedor> achado = fornecedorRepository.findByNomeIgnoreCase("VOTORANTIM");

        assertTrue(achado.isPresent());
        assertEquals("Votorantim", achado.get().getNome());
    }

    @Test
    void deveListarApenasAtivosEmOrdemAlfabetica() {
        fornecedorRepository.save(Fornecedor.builder().nome("Tigre").build());
        fornecedorRepository.save(Fornecedor.builder().nome("Amanco").build());
        fornecedorRepository.save(Fornecedor.builder().nome("Inativo").ativo(false).build());

        var ativos = fornecedorRepository.findByAtivoTrueOrderByNomeAsc();

        assertEquals(2, ativos.size());
        assertEquals("Amanco", ativos.get(0).getNome());
        assertEquals("Tigre", ativos.get(1).getNome());
    }
}
```

- [ ] **Step 2: Rodar para verificar que falha**

Run (em `marluse/`): `./mvnw test -Dtest=FornecedorServiceTest`

Expected: FALHA de compilação — `package com.example.marluse.fornecedores.model does not exist`.

- [ ] **Step 3: Criar a entidade**

`marluse/src/main/java/com/example/marluse/fornecedores/model/Fornecedor.java`:

```java
package com.example.marluse.fornecedores.model;

import com.example.marluse.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fornecedores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fornecedor extends BaseEntity {

    @Column(nullable = false, unique = true, length = 120)
    private String nome;

    @Builder.Default
    @Column(nullable = false)
    private boolean ativo = true;

    // Fornecedor vive dentro de um Set em Produto (@ManyToMany). Sem equals/hashCode
    // por id, dois carregamentos do mesmo fornecedor viram elementos distintos do Set
    // e o Hibernate emite INSERTs duplicados na tabela de vínculo.
    // hashCode constante é o padrão recomendado para entidades JPA: o id só existe
    // após o persist, e um hashCode que muda quebra o Set em que a entidade já está.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Fornecedor outro)) return false;
        return getId() != null && getId().equals(outro.getId());
    }

    @Override
    public int hashCode() {
        return Fornecedor.class.hashCode();
    }
}
```

- [ ] **Step 4: Criar o repository**

`marluse/src/main/java/com/example/marluse/fornecedores/repository/FornecedorRepository.java`:

```java
package com.example.marluse.fornecedores.repository;

import com.example.marluse.fornecedores.model.Fornecedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FornecedorRepository extends JpaRepository<Fornecedor, String> {

    Optional<Fornecedor> findByNomeIgnoreCase(String nome);

    List<Fornecedor> findByAtivoTrueOrderByNomeAsc();
}
```

- [ ] **Step 5: Rodar para verificar que passa**

Run (em `marluse/`): `./mvnw test -Dtest=FornecedorServiceTest`

Expected: PASS, 2 testes.

- [ ] **Step 6: Criar a migration**

`marluse/src/main/resources/db/migration/V11__fornecedores.sql`:

```sql
-- Fornecedores de produto (N:N). Campo opcional: nenhum backfill,
-- produtos existentes simplesmente ficam sem fornecedor.

CREATE TABLE fornecedores (
    id         VARCHAR(36)  NOT NULL,
    nome       VARCHAR(120) NOT NULL,
    ativo      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME     NULL,
    updated_at DATETIME     NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_fornecedor_nome UNIQUE (nome)
);

CREATE TABLE produto_fornecedores (
    produto_id    VARCHAR(36) NOT NULL,
    fornecedor_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (produto_id, fornecedor_id),
    CONSTRAINT fk_pf_produto    FOREIGN KEY (produto_id)    REFERENCES produtos (id),
    CONSTRAINT fk_pf_fornecedor FOREIGN KEY (fornecedor_id) REFERENCES fornecedores (id)
);
```

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/example/marluse/fornecedores src/main/resources/db/migration/V11__fornecedores.sql src/test/java/com/example/marluse/fornecedores
git commit -m "feat: entidade Fornecedor e tabela de vinculo produto_fornecedores"
```

---

## Task 3: `FornecedorService.resolverPorNomes` — normalização e criar-na-hora

Este é o coração da feature. Tudo que o operador digita passa por aqui.

**Files:**
- Create: `marluse/src/main/java/com/example/marluse/fornecedores/dto/FornecedorResponse.java`
- Create: `marluse/src/main/java/com/example/marluse/fornecedores/service/FornecedorService.java`
- Modify: `marluse/src/test/java/com/example/marluse/fornecedores/FornecedorServiceTest.java`

- [ ] **Step 1: Escrever os testes que falham**

Adicione ao `FornecedorServiceTest` (dentro da classe, junto dos testes existentes) — e acrescente os imports `java.util.List`, `java.util.Set`, `com.example.marluse.fornecedores.service.FornecedorService` e `org.springframework.beans.factory.annotation.Autowired` já presente:

```java
    @Autowired
    private FornecedorService fornecedorService;

    @Test
    void deveCriarFornecedorQuandoNomeNaoExiste() {
        Set<Fornecedor> resolvidos = fornecedorService.resolverPorNomes(List.of("Votorantim"));

        assertEquals(1, resolvidos.size());
        assertEquals("Votorantim", resolvidos.iterator().next().getNome());
        assertEquals(1, fornecedorRepository.count());
    }

    @Test
    void deveReutilizarFornecedorExistenteIgnorandoCaixaEEspacos() {
        fornecedorRepository.save(Fornecedor.builder().nome("Votorantim").build());

        Set<Fornecedor> resolvidos = fornecedorService.resolverPorNomes(List.of("  votorantim  "));

        assertEquals(1, resolvidos.size());
        assertEquals("Votorantim", resolvidos.iterator().next().getNome());
        assertEquals(1, fornecedorRepository.count(), "não deve ter criado um segundo fornecedor");
    }

    @Test
    void deveDeduplicarNomesRepetidosNaMesmaRequisicao() {
        Set<Fornecedor> resolvidos =
                fornecedorService.resolverPorNomes(List.of("Tigre", "TIGRE", " tigre "));

        assertEquals(1, resolvidos.size());
        assertEquals(1, fornecedorRepository.count());
    }

    @Test
    void deveDescartarNomesVaziosENulos() {
        List<String> nomes = new java.util.ArrayList<>(List.of("Tigre", "   ", ""));
        nomes.add(null);

        Set<Fornecedor> resolvidos = fornecedorService.resolverPorNomes(nomes);

        assertEquals(1, resolvidos.size());
        assertEquals("Tigre", resolvidos.iterator().next().getNome());
    }

    @Test
    void deveRetornarConjuntoVazioParaListaNula() {
        assertTrue(fornecedorService.resolverPorNomes(null).isEmpty());
        assertEquals(0, fornecedorRepository.count());
    }

    @Test
    void deveTruncarNomeAcimaDoLimite() {
        String longo = "A".repeat(200);

        Set<Fornecedor> resolvidos = fornecedorService.resolverPorNomes(List.of(longo));

        assertEquals(120, resolvidos.iterator().next().getNome().length());
    }

    @Test
    void deveListarFornecedoresAtivos() {
        fornecedorService.resolverPorNomes(List.of("Tigre", "Amanco"));

        var lista = fornecedorService.listar();

        assertEquals(2, lista.size());
        assertEquals("Amanco", lista.get(0).nome());
        assertNotNull(lista.get(0).id());
    }
```

- [ ] **Step 2: Rodar para verificar que falha**

Run (em `marluse/`): `./mvnw test -Dtest=FornecedorServiceTest`

Expected: FALHA de compilação — `FornecedorService` não existe.

- [ ] **Step 3: Criar o DTO de resposta**

`marluse/src/main/java/com/example/marluse/fornecedores/dto/FornecedorResponse.java`:

```java
package com.example.marluse.fornecedores.dto;

import com.example.marluse.fornecedores.model.Fornecedor;

public record FornecedorResponse(
        String id,
        String nome
) {
    public static FornecedorResponse from(Fornecedor fornecedor) {
        return new FornecedorResponse(fornecedor.getId(), fornecedor.getNome());
    }
}
```

- [ ] **Step 4: Criar o service**

`marluse/src/main/java/com/example/marluse/fornecedores/service/FornecedorService.java`:

```java
package com.example.marluse.fornecedores.service;

import com.example.marluse.fornecedores.dto.FornecedorResponse;
import com.example.marluse.fornecedores.model.Fornecedor;
import com.example.marluse.fornecedores.repository.FornecedorRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FornecedorService {

    /** Igual ao length da coluna `nome`. */
    private static final int NOME_MAX = 120;

    private final FornecedorRepository fornecedorRepository;

    public List<FornecedorResponse> listar() {
        return fornecedorRepository.findByAtivoTrueOrderByNomeAsc()
                .stream()
                .map(FornecedorResponse::from)
                .toList();
    }

    /**
     * Converte os nomes digitados pelo operador em fornecedores persistidos.
     * Faz trim, descarta vazios, deduplica ignorando caixa e cria os que faltam.
     *
     * <p>A garantia de "um único fornecedor por nome" vive aqui, e não na constraint
     * do banco: em H2 (testes) o UNIQUE é case-sensitive, em MySQL não é.
     */
    @Transactional
    public Set<Fornecedor> resolverPorNomes(List<String> nomes) {
        if (nomes == null) {
            return new LinkedHashSet<>();
        }

        // chave em minúsculas -> nome como o operador digitou (primeira ocorrência vence)
        Map<String, String> unicos = new LinkedHashMap<>();
        for (String bruto : nomes) {
            if (bruto == null) continue;
            String nome = bruto.trim();
            if (nome.isEmpty()) continue;
            if (nome.length() > NOME_MAX) nome = nome.substring(0, NOME_MAX);
            unicos.putIfAbsent(nome.toLowerCase(), nome);
        }

        Set<Fornecedor> resolvidos = new LinkedHashSet<>();
        for (String nome : unicos.values()) {
            resolvidos.add(buscarOuCriar(nome));
        }
        return resolvidos;
    }

    /**
     * `orElseGet` e não `orElse`: `orElse` executaria o save mesmo quando o
     * fornecedor já existe.
     *
     * <p>Duas requisições simultâneas podem passar pelo lookup antes de qualquer
     * insert; a segunda bate na UNIQUE e o salvamento do produto falha. Aceito:
     * o operador tenta de novo e funciona, porque na segunda tentativa o
     * fornecedor já existe. Tratar a corrida exigiria transação separada
     * (`REQUIRES_NEW`) — depois de um flush falhar, o `EntityManager` fica
     * inutilizável e não dá para simplesmente refazer o lookup no `catch`.
     */
    private Fornecedor buscarOuCriar(String nome) {
        return fornecedorRepository.findByNomeIgnoreCase(nome)
                .orElseGet(() -> fornecedorRepository.save(
                        Fornecedor.builder().nome(nome).ativo(true).build()));
    }
}
```

- [ ] **Step 5: Rodar para verificar que passa**

Run (em `marluse/`): `./mvnw test -Dtest=FornecedorServiceTest`

Expected: PASS, 9 testes.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/marluse/fornecedores src/test/java/com/example/marluse/fornecedores
git commit -m "feat: FornecedorService com normalizacao de nomes e criar-na-hora"
```

---

## Task 4: Endpoint `GET /api/fornecedores`

**Files:**
- Create: `marluse/src/main/java/com/example/marluse/fornecedores/controller/FornecedorController.java`

Sem teste dedicado: o controller é uma linha delegando para `FornecedorService.listar()`, já coberto na Task 3. A verificação é manual, no Step 2.

- [ ] **Step 1: Criar o controller**

`marluse/src/main/java/com/example/marluse/fornecedores/controller/FornecedorController.java`:

```java
package com.example.marluse.fornecedores.controller;

import com.example.marluse.fornecedores.dto.FornecedorResponse;
import com.example.marluse.fornecedores.service.FornecedorService;
import com.example.marluse.shared.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/fornecedores")
@RequiredArgsConstructor
public class FornecedorController {

    private final FornecedorService fornecedorService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FornecedorResponse>>> listar() {
        return ResponseEntity.ok(ApiResponse.ok(fornecedorService.listar()));
    }
}
```

Não é preciso mexer no `SecurityConfig`: a regra é `anyRequest().authenticated()`, e o frontend já anexa o token via `auth.interceptor.ts`.

- [ ] **Step 2: Subir a aplicação e verificar o endpoint**

Run (em `marluse/`): `./mvnw spring-boot:run`

Com a app no ar, faça login pelo frontend e, no DevTools do navegador (aba Console), execute:

```js
await (await fetch('/api/fornecedores', { credentials: 'include' })).json()
```

Expected: `{ success: true, data: [] }` (lista vazia — nenhum fornecedor cadastrado ainda). Um `401` significa que a sessão expirou; refaça o login.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/marluse/fornecedores/controller
git commit -m "feat: endpoint GET /api/fornecedores"
```

---

## Task 5: Vincular fornecedores ao Produto (o ponto de risco)

**Files:**
- Modify: `marluse/src/main/java/com/example/marluse/estoque/model/Produto.java`
- Modify: `marluse/src/main/java/com/example/marluse/estoque/repository/ProdutoRepository.java`
- Modify: `marluse/src/main/java/com/example/marluse/estoque/dto/ProdutoRequest.java`
- Modify: `marluse/src/main/java/com/example/marluse/estoque/dto/ProdutoAtualizarRequest.java`
- Modify: `marluse/src/main/java/com/example/marluse/estoque/dto/ProdutoResponse.java`
- Modify: `marluse/src/main/java/com/example/marluse/estoque/service/ProdutoService.java`
- Modify: `marluse/src/test/java/com/example/marluse/estoque/ProdutoServiceTest.java`

- [ ] **Step 1: Atualizar os construtores dos records no teste existente**

`ProdutoRequest` e `ProdutoAtualizarRequest` vão de 9 para 10 componentes; sem isso o teste não compila. Em `ProdutoServiceTest`, o helper `produtoValido` (linha ~41) passa a receber os fornecedores:

```java
    private ProdutoRequest produtoValido(String nome, int quantidade) {
        return produtoValido(nome, quantidade, List.of());
    }

    private ProdutoRequest produtoValido(String nome, int quantidade, List<String> fornecedores) {
        return new ProdutoRequest(
                nome, "Descrição",
                new BigDecimal("10.00"),   // valorCompra
                new BigDecimal("25.00"),   // preco
                new BigDecimal("5.00"),    // precoDiaria
                BigDecimal.valueOf(quantidade), 5,
                UnidadeMedida.SACO,
                CategoriaProduto.OUTROS,
                fornecedores);
    }
```

E a chamada de `new ProdutoAtualizarRequest(...)` (linha ~88) ganha um último argumento `null` — que, pelo contrato de patch parcial, significa "não mexe nos fornecedores".

- [ ] **Step 2: Escrever os testes que falham**

Adicione ao `ProdutoServiceTest` (imports novos: `com.example.marluse.fornecedores.repository.FornecedorRepository`, `java.util.List`):

```java
    @Autowired
    private FornecedorRepository fornecedorRepository;

    @Test
    void deveCriarProdutoComFornecedoresNovos() {
        ProdutoResponse response =
                produtoService.criar(produtoValido("Cimento", 50, List.of("Votorantim", "Tigre")));

        assertEquals(List.of("Tigre", "Votorantim"), response.fornecedores());
        assertEquals(2, fornecedorRepository.count());
    }

    @Test
    void deveReaproveitarFornecedorEntreProdutos() {
        produtoService.criar(produtoValido("Cimento", 50, List.of("Votorantim")));
        produtoService.criar(produtoValido("Areia", 30, List.of("votorantim")));

        assertEquals(1, fornecedorRepository.count(), "mesmo fornecedor não deve ser duplicado");
    }

    @Test
    void deveCriarProdutoSemFornecedores() {
        ProdutoResponse response = produtoService.criar(produtoValido("Cimento", 50));

        assertTrue(response.fornecedores().isEmpty());
    }

    @Test
    void deveSubstituirOsFornecedoresNoUpdate() {
        ProdutoResponse criado =
                produtoService.criar(produtoValido("Cimento", 50, List.of("Votorantim")));

        ProdutoResponse atualizado = produtoService.atualizar(criado.id(),
                atualizarComFornecedores(List.of("Tigre")));

        assertEquals(List.of("Tigre"), atualizado.fornecedores());
    }

    @Test
    void deveLimparOsFornecedoresComListaVazia() {
        ProdutoResponse criado =
                produtoService.criar(produtoValido("Cimento", 50, List.of("Votorantim")));

        ProdutoResponse atualizado = produtoService.atualizar(criado.id(),
                atualizarComFornecedores(List.of()));

        assertTrue(atualizado.fornecedores().isEmpty());
    }

    @Test
    void devePreservarOsFornecedoresQuandoOCampoVemNulo() {
        ProdutoResponse criado =
                produtoService.criar(produtoValido("Cimento", 50, List.of("Votorantim")));

        ProdutoResponse atualizado = produtoService.atualizar(criado.id(),
                atualizarComFornecedores(null));

        assertEquals(List.of("Votorantim"), atualizado.fornecedores());
    }

    private ProdutoAtualizarRequest atualizarComFornecedores(List<String> fornecedores) {
        return new ProdutoAtualizarRequest(
                "Cimento", "Descrição",
                new BigDecimal("10.00"),
                new BigDecimal("25.00"),
                new BigDecimal("5.00"),
                BigDecimal.valueOf(50), 5,
                UnidadeMedida.SACO,
                CategoriaProduto.OUTROS,
                fornecedores);
    }
```

Ajuste também o `setUp()` para limpar fornecedores — a tabela de vínculo precisa sair antes:

```java
    @BeforeEach
    void setUp(){
        produtoRepository.deleteAll();
        fornecedorRepository.deleteAll();
    }
```

- [ ] **Step 3: Rodar para verificar que falha**

Run (em `marluse/`): `./mvnw test -Dtest=ProdutoServiceTest`

Expected: FALHA de compilação — `ProdutoRequest` não aceita 10 argumentos e `ProdutoResponse` não tem `fornecedores()`.

- [ ] **Step 4: Adicionar a coleção em `Produto`**

Em `marluse/src/main/java/com/example/marluse/estoque/model/Produto.java`, adicione os imports e o campo ao final da classe, depois de `rascunho`:

```java
import com.example.marluse.fornecedores.model.Fornecedor;
import java.util.LinkedHashSet;
import java.util.Set;
```

```java
    // LAZY de propósito: EAGER num @ManyToMany dispara uma query por produto na
    // listagem de estoque. Os finders que alimentam ProdutoResponse usam @EntityGraph.
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "produto_fornecedores",
            joinColumns = @JoinColumn(name = "produto_id"),
            inverseJoinColumns = @JoinColumn(name = "fornecedor_id"))
    private Set<Fornecedor> fornecedores = new LinkedHashSet<>();
```

`Produto` já importa `jakarta.persistence.*`, então `@ManyToMany`, `@JoinTable` e `@JoinColumn` não precisam de import novo.

- [ ] **Step 5: Adicionar `@EntityGraph` nos finders**

Esta é a proteção contra `LazyInitializationException`. `ProdutoResponse.from()` é chamado **fora de transação** por `listar()`, `listarRascunho()`, `burcarPorId()` e `listarEstoqueBaixo()`. O `@EntityGraph` traz a coleção já inicializada na própria query — e de quebra evita N+1.

`marluse/src/main/java/com/example/marluse/estoque/repository/ProdutoRepository.java` passa a ser:

```java
package com.example.marluse.estoque.repository;

import com.example.marluse.estoque.model.Produto;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, String> {

    Optional<Produto> findByNome(String nome);

    List<Produto> findByAtivoTrue();

    List<Produto> findByQuantidadeEstoqueLessThanEqualAndAtivoTrue(BigDecimal quantidade);

    // Os quatro finders abaixo alimentam ProdutoResponse.from(), que roda fora de
    // transação. Sem @EntityGraph, a coleção `fornecedores` (LAZY) estoura
    // LazyInitializationException em runtime.
    @Override
    @EntityGraph(attributePaths = "fornecedores")
    Optional<Produto> findById(String id);

    @EntityGraph(attributePaths = "fornecedores")
    @Query("SELECT p FROM Produto p WHERE p.quantidadeEstoque <= p.estoqueMinimo AND p.ativo = true AND p.rascunho = false")
    List<Produto> findEstoqueBaixo();

    @EntityGraph(attributePaths = "fornecedores")
    List<Produto> findByAtivoTrueAndRascunhoTrue();

    @EntityGraph(attributePaths = "fornecedores")
    List<Produto> findByAtivoTrueAndRascunhoFalse();
}
```

- [ ] **Step 6: Adicionar o campo nas três DTOs**

Em `ProdutoRequest.java`, adicione o import `java.util.List` e um último componente ao record (após `categoria`):

```java
        /** Nomes digitados pelo operador. Fornecedor que não existir é criado. */
        List<String> fornecedores
```

Em `ProdutoAtualizarRequest.java`, mesmo import e mesmo componente ao final (após `categoria`):

```java
        /** null = não altera os vínculos; lista vazia = remove todos. */
        List<String> fornecedores
```

Em `ProdutoResponse.java`, adicione os imports `com.example.marluse.fornecedores.model.Fornecedor` e `java.util.List`, o componente `List<String> fornecedores` ao final do record (após `rascunho`), e o mapeamento como último argumento do `new ProdutoResponse(...)` dentro de `from()`:

```java
                produto.getFornecedores().stream()
                        .map(Fornecedor::getNome)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList()
```

- [ ] **Step 7: Ligar no `ProdutoService`**

Em `marluse/src/main/java/com/example/marluse/estoque/service/ProdutoService.java`:

Novos imports:

```java
import com.example.marluse.fornecedores.service.FornecedorService;
```

Nova dependência, ao lado de `produtoRepository`:

```java
    private final FornecedorService fornecedorService;
```

Em `criar(...)`, anote o método com `@Transactional` (o import `jakarta.transaction.Transactional` já existe no arquivo) e adicione ao builder, depois de `.categoria(...)`:

```java
                .fornecedores(fornecedorService.resolverPorNomes(request.fornecedores()))
```

Em `atualizar(...)`, anote o método com `@Transactional` e adicione, junto dos outros `if` de patch parcial:

```java
        // null preserva os vínculos atuais; lista vazia remove todos.
        if (request.fornecedores() != null) {
            produto.setFornecedores(fornecedorService.resolverPorNomes(request.fornecedores()));
        }
```

`criarRascunho(...)` não muda: rascunho nasce sem fornecedor, e o `@Builder.Default` já garante a coleção vazia.

- [ ] **Step 8: Rodar para verificar que passa**

Run (em `marluse/`): `./mvnw test -Dtest=ProdutoServiceTest`

Expected: PASS, incluindo os 6 testes novos.

- [ ] **Step 9: Rodar a suíte inteira**

Run (em `marluse/`): `./mvnw test`

Expected: BUILD SUCCESS, sem falhas novas em relação ao baseline da Task 1.

- [ ] **Step 10: Verificar que não há `LazyInitializationException` fora de transação**

Os testes rodam com `@Transactional` na classe, o que **mascara** o problema de lazy loading. Confirme no ambiente real:

Run (em `marluse/`): `./mvnw spring-boot:run`

No DevTools do navegador, logado:

```js
await (await fetch('/api/produtos', { credentials: 'include' })).json()
```

Expected: `success: true` e cada item com `"fornecedores": []`. Se aparecer erro 500 com `LazyInitializationException`, o `@EntityGraph` do Step 5 não foi aplicado ao finder correspondente.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/example/marluse/estoque src/test/java/com/example/marluse/estoque
git commit -m "feat: vincula fornecedores ao produto (N:N) no create e no update"
```

---

## Task 6: Frontend — models e service

**Files:**
- Modify: `marluse-frontend/src/app/features/estoque/models/estoque.models.ts`
- Create: `marluse-frontend/src/app/features/estoque/fornecedores.service.ts`

- [ ] **Step 1: Adicionar `fornecedores` às interfaces e corrigir o typo**

Em `estoque.models.ts`, adicione `fornecedores` a `ProdutoRequest` (após `categoria`), a `ProdutoResponse` (após `rascunho`) e a `ProdutoAtualizarRequest`. Em `ProdutoResponse` o campo é obrigatório: o backend sempre devolve ao menos uma lista vazia.

```ts
export interface ProdutoRequest {
  // …campos existentes…
  categoria: CategoriaProduto;
  fornecedores?: string[];
}

export interface ProdutoResponse {
  // …campos existentes…
  rascunho: boolean;
  fornecedores: string[];
}
```

Em `ProdutoAtualizarRequest`, **corrija também o typo `categorira` → `categoria`**. Isso é um bug real, não cosmético: o formulário sempre enviou `categoria`, então o tipo descreve um payload que nunca existiu.

```ts
export interface ProdutoAtualizarRequest {
  nome: string;
  descricao?: string;
  valorCompra: number;
  preco: number;
  precoDiaria: number;
  estoqueMinimo: number;
  quantidadeEstoque: number;
  medida: UnidadeMedida;
  categoria: CategoriaProduto;
  fornecedores?: string[];
}
```

- [ ] **Step 2: Criar o service**

`marluse-frontend/src/app/features/estoque/fornecedores.service.ts` — segue o padrão de `estoque.service.ts` (desembrulha o `data` do `ApiResponse`):

```ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface FornecedorResponse {
  id: string;
  nome: string;
}

@Injectable({ providedIn: 'root' })
export class FornecedoresService {
  private http    = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/fornecedores`;

  listar(): Observable<FornecedorResponse[]> {
    return this.http.get<{ data: FornecedorResponse[] }>(this.baseUrl).pipe(map(r => r.data));
  }
}
```

Atenção ao número de `../` no import de `environment`: este arquivo fica em `features/estoque/`, um nível acima de `features/estoque/estoque/`, então são **três** `../`, não quatro.

- [ ] **Step 3: Verificar que compila**

Run (em `marluse-frontend/`): `npx tsc -p tsconfig.app.json --noEmit`

Expected: nenhum erro. Se aparecer erro em `novo-produto-modal.component.ts` sobre `categorira`, é a correção do typo surtindo efeito — o objeto de payload já usa `categoria`, então nada mais precisa mudar ali.

- [ ] **Step 4: Commit**

```bash
git add src/app/features/estoque/models/estoque.models.ts src/app/features/estoque/fornecedores.service.ts
git commit -m "feat: modelo e service de fornecedores no frontend"
```

---

## Task 7: Componente `multi-select-create`

O `app-select-search` existente é single-select (`p-select`) e não aceita valor novo. O campo de fornecedores precisa de chips + digitação livre, o que pede `p-autocomplete` com `[multiple]`.

**Files:**
- Create: `marluse-frontend/src/app/shared/components/multi-select-create/multi-select-create.component.ts`
- Create: `marluse-frontend/src/app/shared/components/multi-select-create/multi-select-create.component.spec.ts`

- [ ] **Step 1: Escrever o teste que falha**

`marluse-frontend/src/app/shared/components/multi-select-create/multi-select-create.component.spec.ts`:

```ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MultiSelectCreateComponent } from './multi-select-create.component';

describe('MultiSelectCreateComponent', () => {
  let component: MultiSelectCreateComponent;
  let fixture: ComponentFixture<MultiSelectCreateComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MultiSelectCreateComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(MultiSelectCreateComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('deve iniciar com lista vazia', () => {
    expect(component.value).toEqual([]);
  });

  it('deve tratar null do writeValue como lista vazia', () => {
    component.writeValue(null as unknown as string[]);
    expect(component.value).toEqual([]);
  });

  it('deve sugerir apenas opções ainda não selecionadas, ignorando caixa', () => {
    component.options = ['Votorantim', 'Tigre', 'Amanco'];
    component.writeValue(['votorantim']);

    component.filtrar({ query: '' } as never);

    expect(component.sugestoes).toEqual(['Tigre', 'Amanco']);
  });

  it('deve filtrar sugestões pelo texto digitado', () => {
    component.options = ['Votorantim', 'Tigre'];

    component.filtrar({ query: 'ti' } as never);

    expect(component.sugestoes).toEqual(['Tigre']);
  });

  it('deve remover duplicatas ao selecionar, comparando sem caixa', () => {
    const emitidos: string[][] = [];
    component.registerOnChange(v => emitidos.push(v));

    component.aoMudar(['Tigre', 'tigre', '  Tigre  ']);

    expect(emitidos.at(-1)).toEqual(['Tigre']);
  });

  it('deve descartar entradas vazias', () => {
    const emitidos: string[][] = [];
    component.registerOnChange(v => emitidos.push(v));

    component.aoMudar(['Tigre', '   ', '']);

    expect(emitidos.at(-1)).toEqual(['Tigre']);
  });
});
```

- [ ] **Step 2: Rodar para verificar que falha**

Run (em `marluse-frontend/`): `npm test -- --run multi-select-create`

Expected: FALHA — não consegue resolver `./multi-select-create.component`.

- [ ] **Step 3: Criar o componente**

`marluse-frontend/src/app/shared/components/multi-select-create/multi-select-create.component.ts`:

```ts
import { Component, Input, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { AutoCompleteModule, AutoCompleteCompleteEvent } from 'primeng/autocomplete';

/**
 * Multi-select em chips que aceita valores novos digitados pelo usuário.
 * Trabalha com nomes (string[]), não com ids — quem resolve nome→entidade é o backend.
 */
@Component({
  selector: 'app-multi-select-create',
  standalone: true,
  imports: [CommonModule, FormsModule, AutoCompleteModule],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => MultiSelectCreateComponent),
      multi: true,
    },
  ],
  template: `
    <p-autocomplete
      [(ngModel)]="value"
      (ngModelChange)="aoMudar($event)"
      (onBlur)="onTouched()"
      (completeMethod)="filtrar($event)"
      [suggestions]="sugestoes"
      [multiple]="true"
      [dropdown]="true"
      [disabled]="disabled"
      [placeholder]="placeholder"
      emptyMessage="Digite para cadastrar um novo"
      appendTo="body"
      styleClass="w-full" />
  `,
})
export class MultiSelectCreateComponent implements ControlValueAccessor {
  /** Nomes já cadastrados, usados como sugestão. */
  @Input() options: string[] = [];
  @Input() placeholder = 'Digite e pressione Enter…';

  value: string[] = [];
  sugestoes: string[] = [];
  disabled = false;

  private onChange = (_: string[]) => {};
  onTouched = () => {};

  filtrar(event: AutoCompleteCompleteEvent): void {
    const termo = (event.query ?? '').trim().toLowerCase();
    const jaEscolhidos = new Set(this.value.map(v => v.toLowerCase()));

    this.sugestoes = this.options
      .filter(opcao => !jaEscolhidos.has(opcao.toLowerCase()))
      .filter(opcao => opcao.toLowerCase().includes(termo));
  }

  aoMudar(valores: string[]): void {
    this.value = this.normalizar(valores);
    this.onChange(this.value);
  }

  /** Trim, descarta vazios e deduplica ignorando caixa — espelha o backend. */
  private normalizar(valores: string[]): string[] {
    const vistos = new Set<string>();
    const saida: string[] = [];

    for (const bruto of valores ?? []) {
      const nome = (bruto ?? '').trim();
      if (!nome) continue;
      const chave = nome.toLowerCase();
      if (vistos.has(chave)) continue;
      vistos.add(chave);
      saida.push(nome);
    }
    return saida;
  }

  writeValue(valores: string[]): void {
    this.value = valores ?? [];
  }

  registerOnChange(fn: (_: string[]) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled = disabled;
  }
}
```

- [ ] **Step 4: Rodar para verificar que passa**

Run (em `marluse-frontend/`): `npm test -- --run multi-select-create`

Expected: PASS, 6 testes.

Se `AutoCompleteCompleteEvent` não for exportado por `primeng/autocomplete` nesta versão, troque o tipo do parâmetro por `{ query: string }` e remova o import — o teste já passa um objeto literal.

- [ ] **Step 5: Commit**

```bash
git add src/app/shared/components/multi-select-create
git commit -m "feat: componente multi-select-create com criacao por digitacao"
```

---

## Task 8: Campo de fornecedores no modal de produto

**Files:**
- Modify: `marluse-frontend/src/app/features/estoque/novo-produto-modal/novo-produto-modal.component.ts`
- Modify: `marluse-frontend/src/app/features/estoque/novo-produto-modal/novo-produto-modal.component.html`

- [ ] **Step 1: Ligar o control no componente**

Em `novo-produto-modal.component.ts`:

Imports novos:

```ts
import { Component, Input, Output, EventEmitter, OnChanges, OnInit, inject } from '@angular/core';
import { MultiSelectCreateComponent } from '../../../shared/components/multi-select-create/multi-select-create.component';
import { FornecedoresService } from '../fornecedores.service';
```

Adicione `MultiSelectCreateComponent` ao array `imports` do decorator, e `OnInit` à cláusula `implements`.

Nova dependência e estado, junto do `private fb = inject(FormBuilder);`:

```ts
  private fornecedoresService = inject(FornecedoresService);

  /** Nomes já cadastrados, usados só como sugestão. */
  fornecedoresDisponiveis: string[] = [];
```

Novo control no `form` (após `categoria`), sem validator — o campo é opcional:

```ts
    fornecedores:      [[] as string[]],
```

Carga das sugestões. A lista é pequena e muda pouco, então carregar uma vez na criação do componente basta:

```ts
  ngOnInit(): void {
    this.fornecedoresService.listar().subscribe({
      next: lista => this.fornecedoresDisponiveis = lista.map(f => f.nome),
      // Sem sugestões o campo continua utilizável (digitação livre) — não bloqueia o cadastro.
      error: () => this.fornecedoresDisponiveis = [],
    });
  }
```

No `ngOnChanges`, no ramo de edição, adicione ao objeto do `form.reset`:

```ts
        fornecedores:      this.produto.fornecedores ?? [],
```

E no ramo de criação, adicione `fornecedores: []` ao `form.reset({...})`.

No `onSalvar`, adicione ao `payload`:

```ts
      fornecedores:      v.fornecedores ?? [],
```

- [ ] **Step 2: Adicionar o campo no template**

Em `novo-produto-modal.component.html`, insira este bloco **depois** do grid "Unidade + Categoria" (que termina na linha ~123) e **antes** do grid "Quantidade + Estoque mínimo":

```html
    <!-- Fornecedores -->
    <div class="flex flex-col gap-1.5">
      <div class="text-[11px] font-semibold text-gray-400 uppercase tracking-[0.06em]">Fornecedores</div>
      <app-multi-select-create formControlName="fornecedores"
        [options]="fornecedoresDisponiveis"
        placeholder="Digite o nome e pressione Enter…" />
      <span class="text-[11px] text-gray-400">Opcional. Fornecedor que ainda não existe é criado ao salvar.</span>
    </div>
```

Sem bloco `@if (… | fieldError)`: o campo não tem validator.

- [ ] **Step 3: Verificar que compila**

Run (em `marluse-frontend/`): `npx tsc -p tsconfig.app.json --noEmit`

Expected: nenhum erro.

- [ ] **Step 4: Rodar a suíte de frontend**

Run (em `marluse-frontend/`): `npm test -- --run`

Expected: nenhuma falha **nova** em relação ao baseline anotado na Task 1.

- [ ] **Step 5: Commit**

```bash
git add src/app/features/estoque/novo-produto-modal
git commit -m "feat: campo de fornecedores no modal de produto"
```

---

## Task 9: Verificação ponta a ponta no navegador

**Files:** nenhum (apenas verificação)

- [ ] **Step 1: Subir backend e frontend**

Run (em `marluse/`): `./mvnw spring-boot:run`
Run (em `marluse-frontend/`): `npm start`

- [ ] **Step 2: Criar produto com fornecedor novo**

Estoque → **Novo produto**. Preencha os campos obrigatórios e, em Fornecedores, digite `Votorantim` + Enter e `Tigre` + Enter. Salve.

Expected: produto criado sem erro; dois chips apareceram enquanto se digitava.

- [ ] **Step 3: Confirmar a persistência e a sugestão**

Abra **Novo produto** de novo e clique na setinha do dropdown de Fornecedores.

Expected: `Tigre` e `Votorantim` aparecem como sugestão, em ordem alfabética.

- [ ] **Step 4: Confirmar que o vínculo volta na edição**

Edite o produto criado no Step 2.

Expected: os dois chips já vêm preenchidos.

- [ ] **Step 5: Confirmar dedup case-insensitive**

Crie um segundo produto e digite `votorantim` (minúsculo) + Enter. Salve. Depois, no console do DevTools:

```js
await (await fetch('/api/fornecedores', { credentials: 'include' })).json()
```

Expected: exatamente **2** fornecedores (`Tigre` e `Votorantim`) — não 3. O segundo produto reaproveitou o `Votorantim` existente.

- [ ] **Step 6: Confirmar que remover funciona**

Edite o segundo produto, remova o chip pelo "x" e salve. Reabra a edição.

Expected: sem fornecedores. O `GET /api/fornecedores` continua devolvendo 2 — remover o vínculo não apaga o cadastro do fornecedor.

- [ ] **Step 7: Confirmar que nada quebrou nas telas vizinhas**

Abra Vendas → Novo pedido e Locações → Nova locação, e confirme que a lista de produtos carrega normalmente. Esses fluxos consomem `ProdutoResponse` e são o lugar onde um `LazyInitializationException` apareceria.

Expected: listas carregam sem erro no console.

- [ ] **Step 8: Commit final (se houve algum ajuste)**

```bash
git add -A
git commit -m "fix: ajustes da verificacao ponta a ponta de fornecedores"
```

---

## Checklist de aceite

- [ ] `./mvnw test` passa em `marluse/`
- [ ] `npm test -- --run` em `marluse-frontend/` sem falhas novas em relação ao baseline
- [ ] `GET /api/produtos` devolve `fornecedores` em cada item, sem erro 500
- [ ] Fornecedor digitado em caixa diferente não vira registro duplicado
- [ ] Produto sem fornecedor continua sendo criado normalmente
- [ ] `V11__fornecedores.sql` bate com o schema que o JPA gera em dev
