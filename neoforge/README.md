# Shifu Chests

Mod para **NeoForge 1.21.1 + Cobblemon 1.8** (modpack Galaxia Cobblemon).

Três baús de fábrica (Normal, Médio, Premium) e quantos mais você quiser: cada arquivo .json em config/shifuchests/ vira um baú com a sua chave (`/baus bloco`, `/baus definir`, `/baus chave`). Clicar no baú com a chave abre
uma roleta animada que sorteia um Pokémon, um item ou um comando. Os prêmios são configurados em
JSON ou por comandos no jogo.

## O que tem

- **Roleta na tela:** cartões com o modelo 3D do Pokémon (ou o item), na cor da raridade, freando
  até parar no prêmio. Depois vem a revelação, com raios de luz, faíscas, confete (épico ou mais),
  brilho de arco-íris para shiny e o grito do Pokémon.
- **Efeitos no mundo para quem está perto:** o baú treme e solta uma espiral de partículas enquanto
  gira. Na revelação ele pula e solta uma explosão, e de raro para cima aparece um feixe de luz na
  cor da raridade.
- **Prévia:** clicar no baú sem chave mostra todos os prêmios, com a chance de cada um.
- **Anúncio no chat** de prêmios raros e de shiny.
- **Seguro para servidor:** o sorteio é feito no servidor. Se o jogador sair no meio da roleta ou o
  servidor fechar, o prêmio é entregue na hora. Quando a equipe está cheia, o Pokémon vai para o PC.
  Cada abertura fica registrada no log.

## Instalação

O mesmo `.jar` vai **no servidor e no cliente** (pasta `mods`). Ele precisa do Cobblemon e do Kotlin
for Forge, que já estão no modpack.

Na primeira vez que o servidor iniciar, ele cria `config/shifuchests/` com exemplos e o guia
`COMO_CONFIGURAR.txt`.

## Uso rápido (operador)

```
/baus chave <jogador> <normal|medio|premium> [quantidade]
/baus adicionar premium pokemon 2 lendario charizard shiny=yes
/baus adicionar medio item 8 auto          (o item que está na sua mão)
/baus listar premium
/baus remover premium 3
/baus recarregar
/baus testar premium                       (gira sem gastar chave e sem ganhar)
/baus girar <jogador> premium              (roleta de graça: lojas, eventos)
```

Os baús são inquebráveis no survival. Para tirar um, use o criativo.

## Compilar

Precisa de um JDK 21 (`JAVA_HOME`).

```
gradlew build                 -> build/libs/shifuchests-<versão>.jar
gradlew runSelfTest           -> servidor de teste com Cobblemon, roda ~120 verificações e desliga
gradlew runClientSelfTest     -> abre o jogo, roda a roleta e salva prints em run/clienttest/screenshots
gradlew runClient             -> jogo de desenvolvimento
```

Os testes ficam em `src/selftest` e não entram no `.jar`.

## Estrutura

```
src/main/java/com/shifuchests/
  bau/          bloco, block entity (animações no mundo), chave, tiers
  config/       leitura/escrita dos JSON, raridades, recompensas
  sorteio/      RollManager: sorteio, entrega, anúncios, sons no mundo
  cobblemon/    ponte com a API do Cobblemon (criar e entregar Pokémon)
  network/      pacotes servidor <-> cliente
  command/      /baus
  client/       renderer do baú, tela da roleta, prévia, partículas da tela
src/main/resources/
  assets/shifuchests/   modelos e texturas do Chests Pack, traduções pt_br/en_us
  shifuchests_padrao/   configs de exemplo copiadas na primeira vez
```
