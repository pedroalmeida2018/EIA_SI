# EIA_SI
Trabalho Prático da UC Segurança Informática

para testar:
ter três terminais para o : relay e dois clientes.
-----
Passo 1 — Abrir o terminal na pasta do projeto
-----
Passo 2 — Compilar:

No terminal, escreve:

Windows:
javac -d out (Get-ChildItem -Recurse src -Filter *.java).FullName

macOS:
javac -d out $(find src -name "*.java")
------
Se não aparecer nenhuma mensagem, a compilação correu bem. É criada a pasta `out` com as classes compiladas.
---
Passo 3 — Correr os testes (opcional)

java -cp out teste.TesteChaves
java -cp out teste.TesteTrama

esultado esperado do `TesteChaves`:
```
Assinatura da Ana, chave da Ana:  true
Dados alterados:                  false
Chave de outro utilizador:        false
Chave reconstruida a partir de 44 bytes: true
Segredos iguais: true (tamanho 32 bytes)
```

Resultado esperado do `TesteTrama`:
```
Tipo: 1
Corpo: ola relay
```
### Passo 4 — Arrancar o relay (terminal 1)
No mesmo terminal, escreva:
```
java -cp out relay.RelayMain
```
Deve aparecer:
```
Relay a escutar no porto 5000
```
O terminal fica ocupado com o relay a correr.Não o fechar.
Passo 5 — Arrancar o cliente "bruno" (terminal 2)

Abra um **segundo terminal**. No VS Code, carregue no **+** no canto superior direito do painel do terminal.
Escreva:
```
java -cp out client.ClientMain bruno
```
Deve aparecer:
```
Registado como 'bruno'.
Comandos: procurar <nome> | enviar <nome> <texto> | sair
```
Deixe este terminal aberto.

### Passo 6 — Arrancar o cliente "ana" (terminal 3)

Abra um **terceiro terminal** (outra vez o **+**) e escreva:
```
java -cp out client.ClientMain ana
```
Deve aparecer:
```
Registado como 'ana'.
Comandos: procurar <nome> | enviar <nome> <texto> | sair
```

### Passo 7 — Procurar o Bruno e enviar-lhe uma mensagem

No terminal da **ana**, escreva uma linha de cada vez, carregando Enter depois de cada uma:
```
procurar bruno
enviar bruno ola, tudo bem?
```

### Passo 8 — Ver o resultado nos três terminais

No VS Code, a lista dos terminais está do lado direito do painel. Clique em cada um.

| Terminal | O que deve aparecer |
|---|---|
| ana | `bruno encontrado (registo com assinatura valida).` e `[enviada para bruno]` |
| bruno | `[hh:mm:ss] ana: ola, tudo bem?` |
| relay | `Reencaminhada mensagem ana -> bruno (... bytes cifrados)` |

O relay mostra apenas o tamanho da mensagem e nunca o texto, porque a mensagem vai cifrada
de ponta a ponta e o relay não tem a chave para a decifrar.

Para responder, escreva no terminal do **bruno**:
```
enviar ana ola Ana!
```

### Passo 9 — Terminar

- Nos clientes, escreva `sair`.
- No relay, carregue `Ctrl+C`.

---

