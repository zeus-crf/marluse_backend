# Fornecedores em Produtos — Design

**Data:** 2026-07-24
**Status:** Aprovado

## Problema

Produtos não registram de quem são comprados. É preciso saber quais fornecedores
oferecem cada produto para consulta de compra.

## Decisões

| Questão | Decisão |
|---|---|
| Natureza | Múltiplos fornecedores por produto (N:N) |
| Cadastro de fornecedor | Mínimo: apenas nome (único) |
| Preço por fornecedor | Não. O vínculo não guarda preço; `valorCompra` do produto segue intocado |
| Obrigatoriedade | Opcional. Sem backfill |
| Onde aparece | Somente no modal de cadastro/edição de produto |
| UX | Multi-select com criar-na-hora (chips) |
| Gestão de fornecedores | Fora de escopo nesta entrega |

O vínculo não guardar preço é deliberado: `Produto.valorCompra` alimenta o cálculo
de custo/lucro em vendas, e duplicar essa informação por fornecedor criaria duas
fontes de verdade para o mesmo número.

## Modelo de dados

Migration `V11__fornecedores.sql`, seguindo o padrão das migrations existentes.

```sql
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

Nenhuma coluna existente muda. Nenhum backfill: produtos atuais ficam sem fornecedor.

A collation padrão do MySQL é case-insensitive, então o `UNIQUE (nome)` já barra
"Votorantim" vs "votorantim" — proteção necessária para o fluxo de criar-na-hora.

## Backend

Novo pacote `com.example.marluse.fornecedores`, espelhando a estrutura de `clientes`.

- `model/Fornecedor` (extends `BaseEntity`): `nome`, `ativo`
- `repository/FornecedorRepository`: `findByNomeIgnoreCase`, `findByAtivoTrueOrderByNomeAsc`
- `service/FornecedorService.resolverPorNomes(List<String>) → Set<Fornecedor>`:
  trim, descarta vazios, deduplica ignorando caixa, busca o existente ou cria.
  Único ponto de contato do `ProdutoService` com fornecedores.
- `controller/FornecedorController`: apenas `GET /api/fornecedores`
- `dto/FornecedorResponse(id, nome)`

Em `Produto`: `@ManyToMany(fetch = LAZY)` + `@JoinTable("produto_fornecedores")`.

`ProdutoResponse.from()` roda fora de transação hoje. Para a coleção LAZY não
estourar `LazyInitializationException`, os finders do `ProdutoRepository` usados
por `listar`, `listarRascunho`, `listarEstoqueBaixo` e `findById` recebem
`@EntityGraph(attributePaths = "fornecedores")`. Isso resolve o lazy e evita N+1
na listagem. EAGER foi descartado: geraria uma query por produto na listagem.

### Contrato

As DTOs trafegam **nomes**, não ids — o frontend nunca manipula id de fornecedor,
o que torna o criar-na-hora trivial.

- `ProdutoRequest.fornecedores: List<String>`
- `ProdutoAtualizarRequest.fornecedores: List<String>`
- `ProdutoResponse.fornecedores: List<String>` (ordenado por nome)

No `atualizar`, seguindo o padrão de patch parcial existente:
`null` = não mexe, `[]` = remove todos os vínculos.

## Frontend

- `estoque.models.ts`: `fornecedores?: string[]` nas três interfaces.
  Corrigir também o typo `categorira` → `categoria` em `ProdutoAtualizarRequest`
  (o tipo hoje diverge do payload que o formulário realmente envia).
- `features/estoque/fornecedores.service.ts`: apenas `listar()`
- `shared/components/multi-select-create/`: novo componente CVA no molde do
  `select-search`, baseado em `p-autocomplete` com `[multiple]` e `[dropdown]`.
  Valor: `string[]`. Chips, filtro e criação por Enter.
- `novo-produto-modal`: control `fornecedores: [[]]` sem validator; carrega
  opções no próprio modal; preenche no `ngOnChanges`; envia no payload.
  `estoque.component` não é tocado.

## Erros

- Falha ao carregar a lista de fornecedores: o campo continua funcional (sem
  sugestões, digitação livre). Não bloqueia o salvamento do produto.
- Nome limitado a 120 caracteres; nomes vazios ou só espaços são descartados.

### Limitação aceita: corrida na criação

Duas requisições simultâneas criando o mesmo fornecedor novo podem passar as duas
pelo lookup antes de qualquer insert. A segunda viola a `UNIQUE` e o salvamento
daquele produto falha. O operador tenta de novo e funciona, porque na segunda
tentativa o fornecedor já existe — e a `UNIQUE` garante que nunca haja duplicata,
que é o ponto que realmente importa.

Tratar a corrida no código exigiria criar o fornecedor numa transação separada
(`REQUIRES_NEW`): capturar `DataIntegrityViolationException` e refazer o lookup no
mesmo `EntityManager` não funciona, porque depois de um flush falhar o Hibernate
marca a transação como rollback-only e a sessão fica inutilizável. Essa máquina
não se justifica para o volume de uso esperado (poucos operadores simultâneos).

## Testes

Em `ProdutoServiceTest`:
- criar produto com nomes novos e existentes na mesma requisição
- `" votorantim "` e `"Votorantim"` resolvem para um único fornecedor
- update substitui o conjunto de vínculos
- update com `[]` limpa os vínculos
- update com `null` preserva os vínculos

## Fora de escopo

Tela de gestão de fornecedores, renomear/excluir, coluna na listagem de estoque,
filtro por fornecedor, preço por fornecedor, dados de contato (telefone/CNPJ).
