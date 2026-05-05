# IA Agents - Revue complete (Code + Logique)

Ce document est une fiche de revision complete sur les agents IA du projet RaaS.
Il couvre:
- architecture globale,
- details classes/methodes,
- flux d'execution backend,
- logique metier,
- securite multi-tenant,
- fallback et gestion d'erreurs,
- points de test.

---

## 1) Vue d'ensemble

Le projet contient **2 agents IA** cote backend:

1. **Agent Documentation** (`RuleSetDocumentationAgent`)
   - Genere automatiquement une documentation metier en langage naturel pour un RuleSet.
   - Sauvegarde la sortie en base dans `ai_insights`.

2. **Agent Chatbot** (`ChatService` + `ChatController`)
   - Repond aux questions utilisateur sur la plateforme.
   - Conserve l'historique de conversation (`chat_conversations`, `chat_messages`).
   - Filtre les questions hors contexte projet.

Les deux agents utilisent une abstraction commune:
- Interface: `LlmClient`
- Implementation: `OpenAiLlmClient` (migre vers **Spring AI** via `ChatModel`).

---

## 2) Architecture technique (cote code)

### 2.1 Couches principales

- **Controller**
  - `ChatController` pour `/api/chat`
  - `AiInsightController` pour consulter/generer docs IA et exporter PDF

- **Services metier**
  - `ChatService` (orchestration conversation + contexte)
  - `RuleSetDocumentationAgent` (generation doc asynchrone)
  - `AiInsightService` (lecture/status/generation manuelle des insights)
  - `DocumentationPdfService` (export PDF guide + documentation)

- **LLM abstraction**
  - `LlmClient` (contrat commun)
  - `OpenAiLlmClient` (appel LLM via Spring AI)

- **Persistence**
  - `AiInsightRepository`
  - `ChatConversationRepository`
  - `ChatMessageRepository`
  - `RuleSetRepository`

---

## 3) Agent 1 - Documentation IA

### 3.1 Classe
- `talan.pfe.rulengine.services.serviceImpl.RuleSetDocumentationAgent`

### 3.2 Objectif
Produire une documentation lisible metier d'un RuleSet actif:
- objectif du RuleSet,
- logique de decision,
- interpretation des regles,
- usages et limites.

### 3.3 Flux detaille

1. **Declenchement**
   - Lors des operations metier de version/activation RuleSet.
   - Execution asynchrone (`@Async`) pour ne pas bloquer la requete utilisateur.

2. **Chargement des donnees**
   - Recupere RuleSet cible avec regles/conditions/actions.
   - Initialise les collections lazy pour un contexte complet.

3. **Construction des prompts**
   - `systemPrompt`: comportement du modele (expert metier, francais clair).
   - `userPrompt`: details structurels du RuleSet (strategie, priorites, conditions, actions).

4. **Generation LLM**
   - Appel `llmClient.generate(systemPrompt, userPrompt)`.

5. **Fallback**
   - Si erreur LLM (cle absente, provider indisponible, etc.):
   - Genere un texte local simplifie via `buildFallback(...)`.

6. **Persist**
   - Sauvegarde un `AiInsight`:
     - `type = DOCUMENTATION_GENERATED`
     - `agentType = DOCUMENTATION_AGENT`
     - `title`, `description`, `contextData`, `confidence`
   - Lie l'insight au tenant + ruleset.

### 3.4 Valeur metier
- Documentation auto pour profils non-techniques.
- Trace versionnee et validable (Accept/Reject).
- Base exploitable pour audits et diffusion.

---

## 4) Agent 2 - Chatbot contextuel

### 4.1 Classes
- `talan.pfe.rulengine.controllers.ChatController`
- `talan.pfe.rulengine.services.serviceImpl.ChatService`
- DTOs: `ChatRequest`, `ChatResponse`

### 4.2 Endpoint
- `POST /api/chat`
- Securite: `@PreAuthorize("hasAnyRole('GLOBAL_ADMIN','ADMIN','MANAGER','VIEWER')")`

### 4.3 Flux detaille

1. **Authentification & tenant**
   - `CurrentUserResolver.requireUser()`
   - Verifie la presence tenant (sinon `BadRequestException`).

2. **Conversation**
   - Si `conversationId` absent -> cree une nouvelle conversation.
   - Sinon -> verifie ownership (meme tenant + meme user).

3. **Persist message user**
   - Cree `ChatMessage` role `USER`.

4. **Recuperation historique**
   - Charge derniers messages (top 12), trie chronologiquement.
   - Convertit en format `LlmClient.Message`.

5. **Filtre hors contexte projet**
   - Methode `isProjectScopedQuestion(...)`.
   - Detecte mots-cles projet (ruleset, regle, action, strategie, api key, version, etc.).
   - Si hors scope: reponse de refus guidee, sans appel LLM.

6. **Prompt systeme contextualise**
   - Role utilisateur
   - Tenant
   - Liste RuleSets accessibles
   - RuleSet cible (si `ruleSetId` fourni)

7. **Appel LLM**
   - `llmClient.chat(systemPrompt, history, userPrompt)`
   - Catch exception -> message fallback "modele indisponible".

8. **Persist message assistant**
   - Sauvegarde `ChatMessage` role `ASSISTANT`.

9. **Response API**
   - Retourne `conversationId` + `reply`.

### 4.4 Valeur metier
- Assistance contextualisee au perimetre reel du user.
- Historique complet.
- Protection contre reponses hors sujet.

---

## 5) Integration Spring AI (nouvelle version)

### 5.1 Dependances Maven
- BOM: `org.springframework.ai:spring-ai-bom:1.0.0`
- Starter: `org.springframework.ai:spring-ai-starter-model-openai`

### 5.2 Config applicative
Dans `application.properties`:

- `spring.ai.openai.api-key`
- `spring.ai.openai.base-url`
- `spring.ai.openai.chat.options.model`
- `spring.ai.openai.chat.options.temperature`

Le projet conserve aussi les proprietes `llm.*` comme couche de mapping pratique.

### 5.3 Implementation LLM

`OpenAiLlmClient`:
- injecte `ChatModel` (Spring AI),
- construit un `Prompt` avec:
  - `SystemMessage`
  - historique (`UserMessage` / `AssistantMessage`)
  - message user courant,
- appelle `chatModel.call(...)`,
- recupere le texte via `response.getResult().getOutput().getText()`.

### 5.4 Pourquoi ce choix
- Moins de code HTTP bas niveau a maintenir.
- Standardisation provider-compatible OpenAI.
- Evolution plus facile vers fonctions IA avancees.

---

## 6) Securite et isolation multi-tenant

Points critiques assures par le code:

- Verification identite user avant chat.
- Validation tenant obligatoire.
- Conversation accessible uniquement au proprietaire (tenant + user).
- Endpoint chat protege par roles.
- Aucune exposition de secret dans les reponses.
- Filtre hors contexte pour limiter derive du chatbot.

---

## 7) Gestion d'erreurs et resilience

- Exceptions metier -> `BadRequestException`, `ResourceNotFoundException`, etc.
- Handler global -> reponses JSON homogenes.
- Fallback chatbot si LLM indisponible.
- Fallback documentation si generation IA indisponible.
- PDF: rendu HTML/CSS + fallback renderer en cas d'echec.

---

## 8) Endpoints importants a connaitre

- Chat:
  - `POST /api/chat`

- Insights / documentation:
  - `GET /api/rulesets/{ruleSetId}/insights/documentations`
  - `GET /api/rulesets/{ruleSetId}/insights/latest-documentation`
  - `PATCH /api/insights/{insightId}/status`
  - `POST /api/rulesets/{ruleSetId}/insights/generate`

- Export PDF:
  - `GET /api/rulesets/{ruleSetId}/insights/latest-documentation/pdf`
  - `GET /api/documentation/engine-guide/pdf`

---

## 9) Checklist de revision (rapide avant demo)

### Chatbot
- [ ] Une question in-scope repond correctement (regles/strategies/rulesets)
- [ ] Une question hors scope est refusee proprement
- [ ] Les messages sont historises en DB
- [ ] Une conversation existante est reprise correctement

### Documentation Agent
- [ ] Generation auto apres activation/version
- [ ] Insight present dans `ai_insights`
- [ ] Status update ACCEPTED/REJECTED fonctionne
- [ ] Fallback s'active si LLM KO

### PDF
- [ ] Download guide PDF OK
- [ ] Download documentation PDF RuleSet OK
- [ ] Pas de 400/500 sur endpoints PDF

---

## 10) Limites actuelles et ameliorations futures

### Limites
- Filtre hors contexte base sur mots-cles (simple, non semantique).
- Pas de scoring de confiance conversationnel explicite.
- Pas de RAG (recherche vectorielle) pour enrichir les reponses sur base documentaire.

### Evolutions conseillees
- Ajouter filtrage semantique (classification intent in/out scope).
- Ajouter RAG sur docs internes (guide, policies, specs).
- Ajouter metrics (latence, taux fallback, taux refus hors contexte).
- Ajouter tests automatises dedies au comportement IA.

---

## 11) Resume final

- Le projet embarque bien **2 agents IA**:
  1) **Documentation Agent**
  2) **Chatbot Agent**

- Les 2 passent par une abstraction LLM commune (`LlmClient`) et exploitent Spring AI.

- L'architecture est multi-tenant, securisee, avec fallback et persistence, donc adaptee a un contexte de production encadre.

### 11.1 Historique des conversations chatbot (nouveau)

Le chatbot n'est pas statique: il persiste les conversations et permet desormais de les rouvrir.

#### Backend ajoute

- **Endpoints**
  - `GET /api/chat/conversations`
    - Retourne la liste des conversations de l'utilisateur courant (tenant + user), triées par derniere activite.
    - DTO: `ChatConversationSummaryResponse`
      - `conversationId`
      - `lastMessagePreview`
      - `updatedAt`
  - `GET /api/chat/conversations/{conversationId}/messages`
    - Retourne tous les messages d'une conversation (ordre chronologique).
    - DTO: `ChatMessageResponse`
      - `id`, `role`, `content`, `createdAt`

- **Services**
  - `ChatService.listConversations()`
    - Lit les conversations utilisateur/tenant.
    - Construit un apercu du dernier message.
  - `ChatService.getConversationMessages(conversationId)`
    - Verifie l'appartenance conversation (tenant + user).
    - Retourne la conversation complete.

- **Repositories**
  - `ChatConversationRepository.findByTenantIdAndUserIdOrderByUpdatedAtDesc(...)`
  - `ChatMessageRepository.findByConversationIdOrderByCreatedAtAsc(...)`
  - `ChatMessageRepository.findTop1ByConversationIdOrderByCreatedAtDesc(...)`

#### Frontend ajoute

- **Service Angular** `AiAssistantService`
  - `listConversations()`
  - `getConversationMessages(conversationId)`

- **Widget chat**
  - Ajout d'une barre "Historique des conversations".
  - Selection d'une conversation -> recharge immediate des anciens messages.
  - Rafraichissement de l'historique apres chaque nouvelle reponse IA.

#### Ce que l'utilisateur peut faire

- Reprendre une conversation d'un ancien jour.
- Voir les anciens echanges sauvegardes.
- Basculer entre conversations sans perdre le contexte stocke en base.

#### Diagramme de sequence (texte) - Historique chatbot

1. **User** ouvre le widget chat.
2. **ChatWidget (Angular)** appelle `GET /api/chat/conversations`.
3. **ChatController** delegue a `ChatService.listConversations()`.
4. **ChatService** lit `chat_conversations` + dernier message (`chat_messages`) puis retourne la liste.
5. **User** selectionne une conversation dans l'historique.
6. **ChatWidget** appelle `GET /api/chat/conversations/{id}/messages`.
7. **ChatController** delegue a `ChatService.getConversationMessages(id)`.
8. **ChatService** verifie ownership (tenant + user), lit les messages chronologiques, retourne DTOs.
9. **ChatWidget** affiche les anciens messages.
10. **User** envoie un nouveau message.
11. **POST /api/chat** enregistre USER message, reconstruit contexte recent, appelle LLM, enregistre ASSISTANT message.
12. **ChatWidget** rafraichit la liste historique (conversation remontee en tete via `updatedAt`).

#### Pitch jury (ultra-court)

Le chatbot conserve toutes les conversations en base, par utilisateur et par tenant.  
Au chargement, l'UI liste l'historique et permet de rouvrir n'importe quelle conversation ancienne.  
Chaque nouveau message enrichit la conversation existante, met a jour son `updatedAt`, et remonte cette conversation en tete de liste.  
Le systeme est securise (RBAC + verification ownership) et garde un contexte conversationnel persistant entre sessions/jours.

#### Dernieres ameliorations UI (chatbot)

Pour rendre l'experience proche de ChatGPT, les evolutions suivantes ont ete appliquees:

- **Disposition 2 colonnes en mode embedded**
  - Colonne gauche: liste des conversations.
  - Colonne droite: conversation active.

- **Historique visuel a gauche**
  - Bouton `+ Nouvelle conversation`.
  - Apercu du dernier message.
  - Conversation active mise en avant.

- **Groupement des conversations par jour**
  - Separateurs: `Aujourd'hui`, `Hier`, puis date (`dd/MM/yyyy`).
  - Heures affichees sur chaque item de la liste.

- **Avatars dans les messages**
  - Avatar `U` pour utilisateur.
  - Avatar `AI` pour assistant.
  - Alignement coherent avec les bulles gauche/droite.

- **Refonte visuelle premium**
  - Header, bulles, panneau historique, champs et boutons agrandis.
  - Meilleure hierarchie visuelle (tailles, espacements, contrastes).
  - Responsive mobile conserve (historique passe au-dessus sur petits ecrans).

Fichiers principaux modifies pour cette partie:
- `chat-widget.component.ts`
- `chat-widget.component.html`
- `chat-widget.component.scss`
- `ai-assistant.service.ts`
- `ai.models.ts`

---

## 13) Python vs Spring IA (retour d'experience)

Cette section explique la difference entre une integration IA/LLM typique en Python et l'approche utilisee dans ce projet Spring Boot.

### 13.1 Ce qui reste identique (concept IA)

Que ce soit en Python ou en Java:
- on envoie un prompt systeme + prompt utilisateur (+ historique),
- on appelle un modele LLM via API provider,
- on recoit une reponse textuelle,
- on gere les erreurs de type cle API, quota, modele indisponible.

Autrement dit, la logique IA de base ne change pas.

### 13.2 Ce qui change vraiment

- **Python (habitude courante)**
  - Prototype rapide, notebooks/scripts, experimentation.
  - Excellente vitesse de POC.
  - Moins de "cadre" imposé par defaut.

- **Spring Boot (ce projet)**
  - IA integree dans une application metier complete.
  - Forte integration avec securite, roles, multi-tenant, DB, transactions, audit.
  - Approche plus "enterprise" et production-ready.

### 13.3 Pourquoi cette architecture ici

Le besoin n'est pas seulement "parler au LLM", mais:
- respecter les droits utilisateurs (RBAC),
- isoler les donnees par tenant,
- historiser conversations et insights,
- relier l'IA a des entites metier (RuleSet, Version, Evaluation),
- fournir UI + PDF + endpoints API stables.

Spring est tres adapte a ce type de contexte applicatif.

### 13.4 Comment l'IA est branchee dans ce projet

- Le metier appelle une abstraction `LlmClient`.
- L'implementation concrete est `OpenAiLlmClient` (via Spring AI `ChatModel`).
- Les services metier (chat/documentation) restent decouples du provider.
- Changer OpenAI/Groq se fait surtout par configuration (`base-url`, `model`, `api-key`).

### 13.5 Avantages obtenus vs scripts Python seuls

- **Gouvernance**
  - prompts contextualises par tenant/user/ruleset.
- **Traçabilite**
  - conversations et documents IA persistes en base.
- **Robustesse**
  - fallback si LLM indisponible.
- **Securite**
  - acces controle par role + verification ownership.
- **UX**
  - chatbot integre avec historique type ChatGPT.

### 13.6 Limites / compromis

- Dev plus "lourd" qu'un prototype Python rapide.
- Plus de code applicatif a maintenir (DTOs, controllers, services, security).
- Besoin de discipline DevOps (variables d'env, secrets, monitoring).

### 13.7 Conclusion pratique

Python reste ideal pour explorer et prototyper rapidement l'IA.  
Dans ce projet, Spring Boot est pertinent car l'objectif est d'integrer l'IA au coeur d'une plateforme metier complete, securisee, multi-tenant et exploitable en conditions reelles.

---

## 12) LLM utilise (modele, provider, configuration)

### 12.1 Stack LLM actuelle

Le projet utilise:
- **Spring AI** cote integration Java,
- une implementation applicative `OpenAiLlmClient`,
- un provider **OpenAI-compatible** (OpenAI ou Groq selon `base-url`),
- un modele configure via propriete (`llm.model`).

Concretement, le code n'est pas verrouille sur un seul fournisseur:
- si `base-url` pointe OpenAI -> OpenAI,
- si `base-url` pointe Groq OpenAI-compatible -> Groq.

### 12.2 Proprietes utilisees

Proprietes de haut niveau conservees dans le projet:
- `llm.base-url`
- `llm.api-key`
- `llm.model`
- `llm.temperature`

Mapping vers Spring AI:
- `spring.ai.openai.api-key=${llm.api-key}`
- `spring.ai.openai.base-url=${llm.base-url}`
- `spring.ai.openai.chat.options.model=${llm.model}`
- `spring.ai.openai.chat.options.temperature=${llm.temperature}`

### 12.3 Exemples de configuration

#### OpenAI
```properties
llm.base-url=https://api.openai.com
llm.api-key=${LLM_API_KEY:}
llm.model=gpt-4o-mini
llm.temperature=0.2
```

#### Groq (OpenAI-compatible)
```properties
llm.base-url=https://api.groq.com/openai
llm.api-key=${LLM_API_KEY:}
llm.model=llama-3.1-8b-instant
llm.temperature=0.2
```

Remarque: pour Groq, garder un modele supporte par Groq.

### 12.4 Quel LLM est appele dans le code

Point d'appel principal:
- `OpenAiLlmClient.chat(...)`

Sequence technique:
1. `ChatService` ou `RuleSetDocumentationAgent` prepare prompt + historique.
2. `OpenAiLlmClient` construit `Prompt` Spring AI.
3. Appel `chatModel.call(prompt)`.
4. Recuperation resultat via `response.getResult().getOutput().getText()`.

### 12.5 Parametres qui influencent la qualite

- `model`: precision/coût/latence
- `temperature`:
  - faible (0.1 a 0.3): reponses stables et factuelles
  - plus haute (>0.6): plus creatif, moins deterministe

Pour ce projet metier (règles), temperature basse est recommandee.

### 12.6 Bonnes pratiques de securite LLM

- Ne jamais hardcoder la cle API dans le code.
- Utiliser variable d'environnement (`LLM_API_KEY`).
- Limiter les logs contenant prompts/reponses sensibles.
- En production: rotation periodique des cles.

### 12.7 Fallback si LLM indisponible

- Chatbot: message explicite "modele indisponible"
- Documentation: generation de texte fallback local

Ce fallback garantit que l'application reste utilisable meme si le provider IA est temporairement indisponible.

### 12.8 Quel modele choisir (guide soutenance)

Choix pratique selon objectif:

- **Priorite cout + rapidite**
  - Utiliser un modele "mini"/"instant".
  - Ideal pour chatbot operationnel au quotidien.
  - Exemple: `gpt-4o-mini` ou equivalent OpenAI-compatible rapide.

- **Priorite qualite de redaction**
  - Utiliser un modele plus puissant.
  - Ideal pour documentation IA (meilleure structure, meilleure nuance metier).
  - Cout et latence souvent plus eleves.

- **Priorite stabilite metier**
  - Garder `temperature` basse (0.1 a 0.3).
  - Favorise des reponses coherentes et moins "creatives".

Recommendation projet RaaS: 
- **Chatbot**: modele rapide/economique.
- **Documentation Agent**: modele plus qualitatif si budget permis.
- Maintenir temperature basse pour les deux, car le domaine est reglemente et orienté exactitude.

