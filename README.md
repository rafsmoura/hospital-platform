# Hospital Platform

Backend demonstrativo para agendamento de consultas, historico via GraphQL e processamento assincrono de lembretes. O repositorio contem tres servicos Spring Boot independentes e uma biblioteca compartilhada de contrato de eventos.

## Arquitetura

- `scheduling-service` e dono de usuarios, consultas e outbox.
- `history-service` consome eventos e mantem a projecao consultada por GraphQL.
- `notification-service` consome eventos e persiste o resultado simulado do lembrete.
- PostgreSQL e compartilhado apenas para a tabela minima `users`; cada servico mantem suas tabelas de dominio.
- RabbitMQ distribui eventos de consulta para uma fila de historico e uma fila de notificacoes.

## Pre-requisitos

- Java 21 ou superior compativel com o alvo Maven.
- Maven 3.6.3 ou superior.
- Docker Desktop ou Docker Engine com Compose V2 para executar o ambiente completo.
- Portas livres `5432`, `5672`, `8081`, `8082`, `8083` e `15672`.

O daemon Docker e necessario para PostgreSQL, RabbitMQ e os containers dos servicos. Sem Docker, os testes locais usam H2 e doubles em processo; esse modo nao valida um broker real.

## Execucao Com Compose

Suba toda a plataforma a partir da raiz:

```bash
docker compose up --build
```

O Compose inicia PostgreSQL e RabbitMQ primeiro. Os tres aplicativos aguardam os healthchecks dessas dependencias antes de iniciar. Os dados persistem nos volumes `postgres_data` e `rabbitmq_data`.

Para parar os containers sem apagar dados:

```bash
docker compose down
```

Para apagar tambem os dados locais da demonstracao:

```bash
docker compose down -v
```

Nao use a remocao de volumes em um ambiente que precise preservar dados.

## Maven

Valide compilacao, testes e empacotamento dos quatro modulos:

```bash
mvn -q verify
```

Os jars executaveis ficam em `scheduling-service/target`, `history-service/target` e `notification-service/target`. O teste E2E local usa H2 e entrega de mensagens em processo quando o daemon Docker nao esta disponivel. O fluxo RabbitMQ real deve ser validado com `docker compose up --build`.

## Configuracao

Os servicos aceitam as variaveis abaixo. Os valores apos `:` sao defaults locais.

| Variavel | Servico | Default |
| --- | --- | --- |
| `SERVER_PORT` | cada servico | `8081`, `8082` ou `8083` conforme o servico |
| `DB_URL` | cada servico | `jdbc:postgresql://localhost:5432/hospital` |
| `DB_USERNAME` | cada servico | `hospital` |
| `DB_PASSWORD` | cada servico | `local-dev-only` |
| `RABBITMQ_HOST` | cada servico | `localhost` |
| `RABBITMQ_PORT` | cada servico | `5672` |
| `RABBITMQ_USERNAME` | cada servico | `hospital` |
| `RABBITMQ_PASSWORD` | cada servico | `local-dev-only` |
| `OUTBOX_RELAY_DELAY_MS` | scheduling | `1000` |
| `OUTBOX_CONFIRMATION_TIMEOUT_MS` | scheduling | `5000` |
| `POSTGRES_DB` | Compose | `hospital` |
| `POSTGRES_USER` | Compose | `hospital` |
| `POSTGRES_PASSWORD` | Compose | `local-dev-only` |
| `RABBITMQ_USER` | Compose | `hospital` |
| `RABBITMQ_PASSWORD` | Compose | `local-dev-only` |

Os defaults sao somente para desenvolvimento local. Sobrescreva as credenciais por ambiente e nao publique arquivos de ambiente com secrets.

## Servicos E Health

| Servico | Porta | Health |
| --- | ---: | --- |
| PostgreSQL | `5432` | healthcheck `pg_isready` |
| RabbitMQ AMQP | `5672` | `rabbitmq-diagnostics -q ping` |
| RabbitMQ Management | `15672` | console local do RabbitMQ |
| Scheduling | `8081` | `GET /actuator/health` |
| History | `8082` | `GET /actuator/health` |
| Notification | `8083` | `GET /actuator/health` |

Os health endpoints sao publicos. Os demais endpoints exigem Basic Auth.

## Usuarios Seed

Cada servico que precisa autenticar usuarios aplica a mesma seed demonstrativa. A senha local de todos os usuarios seed e `password`.

| Usuario | Papel | ID |
| --- | --- | --- |
| `admin` | `ADMIN` | `00000000-0000-0000-0000-000000000001` |
| `medico` | `MEDICO` | `00000000-0000-0000-0000-000000000002` |
| `enfermeiro` | `ENFERMEIRO` | `00000000-0000-0000-0000-000000000003` |
| `paciente` | `PACIENTE` | `00000000-0000-0000-0000-000000000004` |

Use esses valores apenas no ambiente local de demonstracao. O backend armazena BCrypt, nunca a senha em claro.

## API REST

Todos os caminhos REST usam o prefixo `/api/v1` no `scheduling-service` (`http://localhost:8081`).

### Criar usuario

`POST /api/v1/users`

Somente `ADMIN`. O corpo e:

```json
{
  "username": "novo-medico",
  "password": "uma-senha-local",
  "role": "MEDICO"
}
```

Papeis aceitos para criacao: `MEDICO`, `ENFERMEIRO` e `PACIENTE`. A resposta `201` contem `id`, `username`, `role` e `active`, sem password ou hash. Usuario duplicado retorna `409`; entrada invalida retorna `400`; demais papeis retornam `403`.

### Criar consulta

`POST /api/v1/appointments`

Somente `ENFERMEIRO`. `scheduledAt` deve ser um `Instant` UTC futuro. O status e opcional na criacao e sempre inicia como `AGENDADA`.

```json
{
  "patientId": "00000000-0000-0000-0000-000000000004",
  "doctorId": "00000000-0000-0000-0000-000000000002",
  "scheduledAt": "2030-09-20T14:00:00Z",
  "notes": "Retorno"
}
```

Uma consulta criada retorna `201` com UUID, paciente, medico, horario, status, notas, versao e timestamps. O paciente deve existir como `PACIENTE`; o medico, quando informado, deve existir como `MEDICO`.

### Consultar consulta

`GET /api/v1/appointments/{id}`

Permitido para `MEDICO` e `ENFERMEIRO`. Retorna `200` sem credenciais, `401` sem autenticacao, `403` para papeis sem permissao e `404` para ID desconhecido.

### Editar consulta

`PUT /api/v1/appointments/{id}`

Permitido para `MEDICO` e `ENFERMEIRO`. O corpo inclui a versao lida pelo cliente:

```json
{
  "patientId": "00000000-0000-0000-0000-000000000004",
  "doctorId": "00000000-0000-0000-0000-000000000002",
  "scheduledAt": "2030-09-21T14:00:00Z",
  "status": "AGENDADA",
  "notes": "Horario atualizado",
  "version": 0
}
```

O status pode ser `AGENDADA`, `REALIZADA` ou `CANCELADA`. Consultas em estado final nao podem ser editadas e retornam `409`. Versao obsoleta tambem retorna `409`; o valor mais novo nao e sobrescrito. Campos invalidos retornam `400`.

## GraphQL

O endpoint e `POST http://localhost:8082/graphql` e exige Basic Auth. O schema implementado e:

```graphql
type Query {
  patientHistory(patientId: ID!, futureOnly: Boolean = false, from: String, sort: AppointmentSort = ASC): [Appointment!]!
  appointment(id: ID!): Appointment
}

type Appointment {
  id: ID!
  patientId: ID!
  doctorId: ID
  scheduledAt: String!
  status: AppointmentStatus!
  notes: String
}

enum AppointmentStatus { AGENDADA REALIZADA CANCELADA }
enum AppointmentSort { ASC DESC }
```

Exemplo de historico futuro para equipe clinica:

```json
{
  "query": "query($patientId: ID!, $from: String!) { patientHistory(patientId: $patientId, futureOnly: true, from: $from, sort: ASC) { id patientId scheduledAt status notes } }",
  "variables": {
    "patientId": "00000000-0000-0000-0000-000000000004",
    "from": "2026-01-01T00:00:00Z"
  }
}
```

`MEDICO` e `ENFERMEIRO` podem consultar o paciente solicitado. `PACIENTE` so pode consultar o ID derivado do usuario autenticado; trocar a variavel `patientId` produz erro GraphQL de autorizacao e nao retorna dados de outro paciente. `futureOnly` filtra por `scheduledAt >= from` em UTC; sem `from`, usa o relogio UTC do servico. Consultas vazias retornam lista vazia. `appointment(id)` retorna o registro apenas se o papel puder ver seu paciente.

## Eventos E Topologia

O scheduling grava consulta e outbox na mesma transacao. O relay publica JSON versionado no exchange duravel `hospital.appointments` usando confirmacao do publisher.

| Elemento | Nome |
| --- | --- |
| Exchange | `hospital.appointments` |
| Routing key criado | `appointment.created` |
| Routing key editado | `appointment.edited` |
| Fila history | `hospital.history.appointments` |
| Fila notification | `hospital.notifications.appointments` |
| Exchange DLX | `hospital.appointments.dlx` |
| DLQ history | `hospital.history.appointments.dlq` |
| DLQ notification | `hospital.notifications.appointments.dlq` |

O contrato `AppointmentEvent` contem `eventVersion`, `messageId`, `eventType`, `occurredAt`, `appointmentId`, `patientId`, `doctorId`, `scheduledAt`, `status` e `notes`. `occurredAt` e todos os horarios sao UTC. `messageId` nasce no outbox e permanece igual durante retries.

### Retry, DLQ E Duplicidade

- O outbox comeca como `PENDING`; confirmacao RabbitMQ marca `PUBLISHED`. Falha de confirmacao marca `FAILED`, preserva erro, tentativas e proximo horario, e permite novo relay.
- O consumer de notificacoes reenvia falhas transientes ate tres tentativas usando o header `x-notification-retry`. Depois rejeita sem requeue e o RabbitMQ encaminha para a DLQ.
- Eventos invalidos sao registrados como falha, rejeitados e nao criam lembrete de sucesso.
- History e notification mantem estado de `messageId` separado. Duplicatas sao reconhecidas sem repetir projecao ou lembrete.
- Uma consulta cancelada gera `CONSULTA_EDITADA`; o notification log registra `SKIPPED_CANCELLED`, sem `SENT`.

## Fluxo Eventual

1. Um `ENFERMEIRO` cria ou um profissional edita uma consulta no scheduling.
2. O scheduling confirma a transacao local e grava um evento no outbox.
3. O relay publica o mesmo `messageId` no exchange depois da confirmacao do broker.
4. RabbitMQ entrega uma copia para history e outra para notification.
5. History atualiza a projecao PostgreSQL; notification grava o lembrete simulado.
6. GraphQL e os registros de notificacao ficam consistentes depois do consumo, nao necessariamente na mesma resposta REST.

O canal de notificacao e somente simulado por persistencia e log estruturado. Nao ha envio externo de email, SMS ou WhatsApp. A implementacao atual tambem nao expoe endpoint HTTP de consulta dos `NotificationLog`; eles sao um registro interno do consumer.

## Basic Auth

Basic Auth foi mantido porque faz parte do contrato demonstrativo. Cada request envia novamente as credenciais. Use HTTPS fora de localhost, proteja o transporte e nunca registre o header `Authorization`. As senhas seed deste README sao defaults publicos de desenvolvimento, nao credenciais adequadas para producao.

## Postman

Importe `postman/hospital-platform.postman_collection.json`. Antes de executar, preencha as variaveis de senha, `patientId`, `otherPatientId` e `doctorId`. A request de criacao salva `appointmentId` e `appointmentVersion` para as requests seguintes. A collection inclui sucesso, 401, 403, historico de equipe, historico proprio e tentativa de isolamento indevido.
