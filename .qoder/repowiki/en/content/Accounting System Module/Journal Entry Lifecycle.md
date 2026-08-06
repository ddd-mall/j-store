# Journal Entry Lifecycle

<cite>
**Referenced Files in This Document**
- [JournalEntry.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt)
- [JournalEntryImpl.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryImpl.kt)
- [AccountingPeriod.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriod.kt)
- [AccountingPeriodImpl.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodImpl.kt)
- [JournalEntryRepository.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryRepository.kt)
- [AccountingPeriodRepository.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodRepository.kt)
- [JournalEntryRepositoryImpl.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryRepositoryImpl.kt)
- [AccountingPeriodRepositoryImpl.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodRepositoryImpl.kt)
- [JournalEntryPO.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/persistence/JournalEntryPO.kt)
- [AccountingPeriodPO.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/persistence/AccountingPeriodPO.kt)
- [JournalEntryPostedEvent.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/event/JournalEntryPostedEvent.kt)
- [JournalEntryReversedEvent.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/event/JournalEntryReversedEvent.kt)
- [AccountingApplicationService.kt](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingApplicationService.kt)
- [AccountingUseCase.kt](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingUseCase.kt)
- [TransactionalAccountingUseCases.kt](file://j-store-accounting-boot/src/main/kotlin/com/jstore/accounting/config/TransactionalAccountingUseCases.kt)
</cite>

## Table of Contents
1. Introduction
2. Project Structure
3. Core Components
4. Architecture Overview
5. Detailed Component Analysis
6. Dependency Analysis
7. Performance Considerations
8. Troubleshooting Guide
9. Conclusion

## Introduction
This document explains the journal entry lifecycle management in the accounting domain, covering the complete workflow from draft creation to posting and reversal. It details validation rules at each stage, accounting period constraints, status transitions (DRAFT → POSTED → REVERSED), integration with AccountingPeriod for date-based restrictions, the post() method implementation, and reversal processes. It also includes examples, error scenarios, and audit trail considerations for financial reporting compliance.

## Project Structure
The journal entry lifecycle is implemented across three layers:
- Domain layer: defines aggregates, value objects, events, and repositories interfaces.
- Application layer: orchestrates use cases and commands.
- Infrastructure layer: implements persistence and repository adapters.

```mermaid
graph TB
subgraph "Domain"
JE["JournalEntry / JournalEntryImpl"]
AP["AccountingPeriod / AccountingPeriodImpl"]
JRepoI["JournalEntryRepository"]
ARepoI["AccountingPeriodRepository"]
Events["JournalEntryPostedEvent<br/>JournalEntryReversedEvent"]
end
subgraph "Application"
AppSvc["AccountingApplicationService"]
UseCase["AccountingUseCase"]
end
subgraph "Infrastructure"
JRepo["JournalEntryRepositoryImpl"]
ARepo["AccountingPeriodRepositoryImpl"]
JPO["JournalEntryPO"]
APO["AccountingPeriodPO"]
end
AppSvc --> UseCase
UseCase --> JE
UseCase --> AP
UseCase --> JRepoI
UseCase --> ARepoI
JRepoI --> JRepo
ARepoI --> ARepo
JRepo --> JPO
ARepo --> APO
JE --> Events
```

**Diagram sources**
- [JournalEntry.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt)
- [JournalEntryImpl.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryImpl.kt)
- [AccountingPeriod.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriod.kt)
- [AccountingPeriodImpl.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodImpl.kt)
- [JournalEntryRepository.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryRepository.kt)
- [AccountingPeriodRepository.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodRepository.kt)
- [JournalEntryRepositoryImpl.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryRepositoryImpl.kt)
- [AccountingPeriodRepositoryImpl.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodRepositoryImpl.kt)
- [JournalEntryPO.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/persistence/JournalEntryPO.kt)
- [AccountingPeriodPO.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/persistence/AccountingPeriodPO.kt)
- [JournalEntryPostedEvent.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/event/JournalEntryPostedEvent.kt)
- [JournalEntryReversedEvent.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/event/JournalEntryReversedEvent.kt)
- [AccountingApplicationService.kt](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingApplicationService.kt)
- [AccountingUseCase.kt](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingUseCase.kt)

**Section sources**
- [JournalEntry.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt)
- [JournalEntryImpl.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryImpl.kt)
- [AccountingPeriod.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriod.kt)
- [AccountingPeriodImpl.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodImpl.kt)
- [JournalEntryRepository.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryRepository.kt)
- [AccountingPeriodRepository.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodRepository.kt)
- [JournalEntryRepositoryImpl.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryRepositoryImpl.kt)
- [AccountingPeriodRepositoryImpl.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodRepositoryImpl.kt)
- [JournalEntryPO.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/persistence/JournalEntryPO.kt)
- [AccountingPeriodPO.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/persistence/AccountingPeriodPO.kt)
- [JournalEntryPostedEvent.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/event/JournalEntryPostedEvent.kt)
- [JournalEntryReversedEvent.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/event/JournalEntryReversedEvent.kt)
- [AccountingApplicationService.kt](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingApplicationService.kt)
- [AccountingUseCase.kt](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingUseCase.kt)

## Core Components
- JournalEntry and JournalLine define the core data model and behaviors for creating, validating, posting, and reversing entries.
- AccountingPeriod enforces open/closed state and date containment checks for posting.
- Repositories abstract persistence; implementations persist entities and support queries needed by application services.
- Events capture posted and reversed states for audit trails and downstream processing.

Key responsibilities:
- Draft creation and line addition are guarded by DRAFT-only state.
- Posting validates period openness, date containment, minimum lines, and balance.
- Reversal creates a mirrored entry with debits/credits flipped and marks original as reversed.

**Section sources**
- [JournalEntry.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt)
- [JournalEntryImpl.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryImpl.kt)
- [AccountingPeriod.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriod.kt)
- [AccountingPeriodImpl.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodImpl.kt)

## Architecture Overview
The lifecycle flows through application use cases that orchestrate domain operations and infrastructure persistence.

```mermaid
sequenceDiagram
participant Client as "Client"
participant App as "AccountingApplicationService"
participant UC as "AccountingUseCase"
participant RepoJE as "JournalEntryRepository"
participant RepoAP as "AccountingPeriodRepository"
participant JE as "JournalEntry"
participant AP as "AccountingPeriod"
Client->>App : Create draft entry + add lines
App->>UC : Execute create/add
UC->>JE : addLine(line)
Note over JE : Validates DRAFT-only and line constraints
Client->>App : Post entry
App->>UC : Execute post(entryId, accountingDate)
UC->>RepoAP : Find period by date
RepoAP-->>UC : AccountingPeriod
UC->>AP : Validate OPEN and contains(date)
UC->>JE : post(AP)
JE-->>UC : Success/Failure
UC->>RepoJE : Save posted entry
UC-->>App : Result
App-->>Client : Posted or Error
Client->>App : Reverse entry
App->>UC : Execute reverse(entryId, reason, date)
UC->>JE : createReversal(...)
JE-->>UC : Reversal entry
UC->>RepoJE : Save reversal
UC->>JE : markReversed(reversalId)
UC-->>App : Result
App-->>Client : Reversed or Error
```

**Diagram sources**
- [AccountingApplicationService.kt](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingApplicationService.kt)
- [AccountingUseCase.kt](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingUseCase.kt)
- [JournalEntryRepository.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryRepository.kt)
- [AccountingPeriodRepository.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodRepository.kt)
- [JournalEntry.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt)
- [AccountingPeriod.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriod.kt)

## Detailed Component Analysis

### JournalEntry Lifecycle and Validation Rules
- Draft creation:
  - Entry must have a non-blank entry number.
  - Lines can be added only when status is DRAFT.
  - Each line requires amount > 0 and non-blank memo.
- Posting:
  - Only DRAFT entries can be posted.
  - Accounting period must be OPEN and contain the accounting date.
  - At least two lines required.
  - Debits must equal credits (balanced).
  - On success, status becomes POSTED and posted timestamp recorded.
- Reversal:
  - Only POSTED entries can be reversed.
  - createReversal builds a new entry with debits/credits flipped and sets reversalOf reference.
  - markReversed updates original entry to REVERSED and records reversal entry id.

```mermaid
flowchart TD
Start(["Start"]) --> AddLine["Add Line"]
AddLine --> CheckDraft{"Status == DRAFT?"}
CheckDraft --> |No| ErrAlreadyPosted["Error: Already posted"]
CheckDraft --> |Yes| ValidateLine["Validate line amount > 0 and memo not blank"]
ValidateLine --> ValidLine{"Valid?"}
ValidLine --> |No| ErrLineInvalid["Error: Invalid line"]
ValidLine --> |Yes| PostCheck["Post Request"]
PostCheck --> CheckPosted{"Status == DRAFT?"}
CheckPosted --> |No| ErrAlreadyPosted
CheckPosted --> |Yes| CheckPeriod["Open period contains accounting date?"]
CheckPeriod --> |No| ErrClosed["Error: Accounting period closed"]
CheckPeriod --> |Yes| CheckLines{"Lines >= 2?"}
CheckLines --> |No| ErrInsufficient["Error: Insufficient lines"]
CheckLines --> |Yes| CheckBalance{"Debits == Credits?"}
CheckBalance --> |No| ErrUnbalanced["Error: Unbalanced entry"]
CheckBalance --> |Yes| SetPosted["Set status=POSTED, set postedAt"]
SetPosted --> End(["End"])
```

**Diagram sources**
- [JournalEntryImpl.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryImpl.kt)
- [JournalEntry.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt)

**Section sources**
- [JournalEntry.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt)
- [JournalEntryImpl.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryImpl.kt)

### AccountingPeriod Integration
- Contains(date): returns true if date falls within [startDate, endDate].
- close(closedBy): transitions to CLOSED with timestamp and actor.
- reopen(reason): transitions back to OPEN.
- Posting uses both status == OPEN and contains(accountingDate) to enforce date-based restrictions.

```mermaid
classDiagram
class AccountingPeriod {
+id : AccountingPeriodId
+periodCode : String
+startDate : LocalDate
+endDate : LocalDate
+status : PeriodStatus
+closedAt : Instant?
+closedBy : String?
+contains(date) : Boolean
+close(closedBy) : Result
+reopen(reason) : Result
}
class AccountingPeriodImpl {
-_status : PeriodStatus
-_closedAt : Instant?
-_closedBy : String?
}
AccountingPeriod <|.. AccountingPeriodImpl
```

**Diagram sources**
- [AccountingPeriod.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriod.kt)
- [AccountingPeriodImpl.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodImpl.kt)

**Section sources**
- [AccountingPeriod.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriod.kt)
- [AccountingPeriodImpl.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodImpl.kt)

### Reversal Process
- createReversal constructs a new JournalEntry with:
  - type = MANUAL_ADJUSTMENT
  - sourceDocument referencing original entry
  - accountingDate provided by caller
  - reversalOf pointing to original entry id
  - all lines mirrored with DEBIT↔CREDIT swapped and memo set to reason
- markReversed updates original entry to REVERSED and stores reversal entry id.

```mermaid
sequenceDiagram
participant Caller as "Caller"
participant JE as "JournalEntry"
Caller->>JE : createReversal(id, no, date, reason)
JE-->>Caller : New JournalEntry (mirrored lines)
Caller->>JE : markReversed(reversalId)
JE-->>Caller : Updated original to REVERSED
```

**Diagram sources**
- [JournalEntryImpl.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryImpl.kt)
- [JournalEntry.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt)

**Section sources**
- [JournalEntryImpl.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryImpl.kt)
- [JournalEntry.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt)

### Status Transitions
- DRAFT → POSTED via post(openPeriod)
- POSTED → REVERSED via markReversed(reversalEntryId)
- No transitions allowed from REVERSED
- DRAFT-only operations: addLine

```mermaid
stateDiagram-v2
[*] --> DRAFT
DRAFT --> POSTED : "post(openPeriod)"
POSTED --> REVERSED : "markReversed(reversalEntryId)"
REVERSED --> [*]
```

**Diagram sources**
- [JournalEntry.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt)
- [JournalEntryImpl.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryImpl.kt)

**Section sources**
- [JournalEntry.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt)
- [JournalEntryImpl.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryImpl.kt)

### Repository and Persistence
- Interfaces:
  - JournalEntryRepository: persists and retrieves JournalEntry aggregates.
  - AccountingPeriodRepository: persists and retrieves AccountingPeriod aggregates.
- Implementations:
  - JournalEntryRepositoryImpl maps to JournalEntryPO.
  - AccountingPeriodRepositoryImpl maps to AccountingPeriodPO.
- These enable transactional save/load operations used by application use cases.

```mermaid
classDiagram
class JournalEntryRepository {
<<interface>>
}
class JournalEntryRepositoryImpl {
+save(entry)
+findById(id)
}
class JournalEntryPO {
<<entity>>
}
JournalEntryRepository <|.. JournalEntryRepositoryImpl
JournalEntryRepositoryImpl --> JournalEntryPO : "persists"
class AccountingPeriodRepository {
<<interface>>
}
class AccountingPeriodRepositoryImpl {
+findByDate(date)
+save(period)
}
class AccountingPeriodPO {
<<entity>>
}
AccountingPeriodRepository <|.. AccountingPeriodRepositoryImpl
AccountingPeriodRepositoryImpl --> AccountingPeriodPO : "persists"
```

**Diagram sources**
- [JournalEntryRepository.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryRepository.kt)
- [JournalEntryRepositoryImpl.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryRepositoryImpl.kt)
- [JournalEntryPO.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/persistence/JournalEntryPO.kt)
- [AccountingPeriodRepository.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodRepository.kt)
- [AccountingPeriodRepositoryImpl.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodRepositoryImpl.kt)
- [AccountingPeriodPO.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/persistence/AccountingPeriodPO.kt)

**Section sources**
- [JournalEntryRepository.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryRepository.kt)
- [JournalEntryRepositoryImpl.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryRepositoryImpl.kt)
- [JournalEntryPO.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/persistence/JournalEntryPO.kt)
- [AccountingPeriodRepository.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodRepository.kt)
- [AccountingPeriodRepositoryImpl.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodRepositoryImpl.kt)
- [AccountingPeriodPO.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/persistence/AccountingPeriodPO.kt)

### Application Orchestration and Transactions
- AccountingApplicationService exposes high-level operations for creating entries, adding lines, posting, and reversing.
- AccountingUseCase encapsulates business logic, invoking domain methods and repositories.
- TransactionalAccountingUseCases ensures database transactions around use case execution.

```mermaid
sequenceDiagram
participant Client as "Client"
participant Svc as "AccountingApplicationService"
participant UC as "AccountingUseCase"
participant TX as "TransactionalAccountingUseCases"
participant Repo as "Repositories"
participant Domain as "JournalEntry / AccountingPeriod"
Client->>Svc : post(entryId, accountingDate)
Svc->>TX : wrapInTransaction()
TX->>UC : execute(post)
UC->>Repo : load period and entry
UC->>Domain : validate and post()
UC->>Repo : save changes
TX-->>Svc : commit
Svc-->>Client : success/failure
```

**Diagram sources**
- [AccountingApplicationService.kt](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingApplicationService.kt)
- [AccountingUseCase.kt](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingUseCase.kt)
- [TransactionalAccountingUseCases.kt](file://j-store-accounting-boot/src/main/kotlin/com/jstore/accounting/config/TransactionalAccountingUseCases.kt)

**Section sources**
- [AccountingApplicationService.kt](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingApplicationService.kt)
- [AccountingUseCase.kt](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingUseCase.kt)
- [TransactionalAccountingUseCases.kt](file://j-store-accounting-boot/src/main/kotlin/com/jstore/accounting/config/TransactionalAccountingUseCases.kt)

## Dependency Analysis
- JournalEntry depends on AccountingPeriod during post() to enforce period constraints.
- Application services depend on repositories to load/save domain objects.
- Infrastructure implementations depend on persistence entities (POs).
- Events are recorded by aggregates to support audit trails and downstream consumers.

```mermaid
graph LR
JE["JournalEntry"] --> AP["AccountingPeriod"]
App["AccountingApplicationService"] --> UC["AccountingUseCase"]
UC --> JRepo["JournalEntryRepository"]
UC --> ARepo["AccountingPeriodRepository"]
JRepo --> JRepoImpl["JournalEntryRepositoryImpl"]
ARepo --> ARepoImpl["AccountingPeriodRepositoryImpl"]
JRepoImpl --> JPO["JournalEntryPO"]
ARepoImpl --> APO["AccountingPeriodPO"]
JE --> Events["Posted/Reversed Events"]
```

**Diagram sources**
- [JournalEntry.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt)
- [AccountingPeriod.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriod.kt)
- [AccountingApplicationService.kt](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingApplicationService.kt)
- [AccountingUseCase.kt](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingUseCase.kt)
- [JournalEntryRepository.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryRepository.kt)
- [AccountingPeriodRepository.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodRepository.kt)
- [JournalEntryRepositoryImpl.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryRepositoryImpl.kt)
- [AccountingPeriodRepositoryImpl.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodRepositoryImpl.kt)
- [JournalEntryPO.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/persistence/JournalEntryPO.kt)
- [AccountingPeriodPO.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/persistence/AccountingPeriodPO.kt)
- [JournalEntryPostedEvent.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/event/JournalEntryPostedEvent.kt)
- [JournalEntryReversedEvent.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/event/JournalEntryReversedEvent.kt)

**Section sources**
- [JournalEntry.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt)
- [AccountingPeriod.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriod.kt)
- [AccountingApplicationService.kt](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingApplicationService.kt)
- [AccountingUseCase.kt](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingUseCase.kt)
- [JournalEntryRepository.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryRepository.kt)
- [AccountingPeriodRepository.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodRepository.kt)
- [JournalEntryRepositoryImpl.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryRepositoryImpl.kt)
- [AccountingPeriodRepositoryImpl.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodRepositoryImpl.kt)
- [JournalEntryPO.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/persistence/JournalEntryPO.kt)
- [AccountingPeriodPO.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/persistence/AccountingPeriodPO.kt)
- [JournalEntryPostedEvent.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/event/JournalEntryPostedEvent.kt)
- [JournalEntryReversedEvent.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/event/JournalEntryReversedEvent.kt)

## Performance Considerations
- Keep line count minimal but sufficient for balanced postings to reduce validation overhead.
- Ensure AccountingPeriod lookups are indexed by date ranges to avoid full scans.
- Batch saves where possible in repository implementations to minimize DB round-trips.
- Avoid unnecessary object copies; reuse validated lines when constructing reversals.

[No sources needed since this section provides general guidance]

## Troubleshooting Guide
Common errors and their causes:
- Accounting period closed:
  - Cause: Attempted to post when period status is CLOSED or accounting date outside period range.
  - Resolution: Open the correct period or adjust accounting date.
- Insufficient lines:
  - Cause: Fewer than two lines present when posting.
  - Resolution: Add additional lines to meet minimum requirement.
- Unbalanced entry:
  - Cause: Sum of debit amounts does not equal sum of credit amounts.
  - Resolution: Adjust line amounts to ensure balance.
- Duplicate posting:
  - Cause: Attempting to post an entry already in POSTED or REVERSED state.
  - Resolution: Create a new entry or reverse first if necessary.
- Invalid reversal state:
  - Cause: Attempting to reverse an entry not in POSTED state or missing reversal reason.
  - Resolution: Ensure entry is POSTED and provide a valid reason.

Audit trail and compliance:
- Events JournalEntryPostedEvent and JournalEntryReversedEvent record key lifecycle transitions.
- Aggregates record domain events to support immutable audit logs and downstream reconciliation.
- Persisted POs maintain historical snapshots for reporting and compliance.

**Section sources**
- [JournalEntryImpl.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntryImpl.kt)
- [JournalEntry.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt)
- [AccountingPeriodImpl.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodImpl.kt)
- [JournalEntryPostedEvent.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/event/JournalEntryPostedEvent.kt)
- [JournalEntryReversedEvent.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/event/JournalEntryReversedEvent.kt)

## Conclusion
The journal entry lifecycle enforces strict validation and state transitions to ensure accurate financial reporting. AccountingPeriod integration guarantees date-based posting controls, while reversal mechanisms preserve auditability and compliance. The layered architecture cleanly separates domain logic, application orchestration, and persistence concerns, enabling robust and maintainable accounting operations.

[No sources needed since this section summarizes without analyzing specific files]