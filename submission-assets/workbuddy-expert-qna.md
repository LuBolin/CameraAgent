# WorkBuddy Experts and Expert Teams — Panel Q&A

Use the first sentence as the direct answer. Add the remaining sentences only if the judge wants more detail. Answers are written for approximately **120 words per minute**.

## One-line mental model

> **The model is the reasoning engine, an Expert supplies a specialist role and tailored context, a Skill adds a capability, and an Expert Team coordinates several experts to divide and cross-check a larger task.**

## Expected questions

### 1. What exactly is a WorkBuddy Expert?

> “A WorkBuddy Expert is a specialized AI persona for a particular domain. WorkBuddy’s Expert Center provides experts across development, design, data, writing and business, each with domain knowledge and tailored prompts. You summon the relevant expert into a task so the same underlying model approaches the work with a more focused professional perspective.”

### 2. What is the difference between an Expert and an Expert Team?

> “An Expert gives one task a specialist perspective. An Expert Team coordinates multiple expert agents around a larger outcome. The team can divide the work, run parts in parallel, cross-check conclusions and return a combined deliverable. For Photo Helper, we used that team principle as a deliberate sequence of specialist passes.”

### 3. Is an Expert just another model?

> “No. The model and expert are separate choices. The model is the reasoning engine; the expert supplies the role, domain framing and tailored instructions. WorkBuddy also separates Skills from Experts: a Skill answers ‘what can the system do?’, while an Expert answers ‘who should help with this task?’”

### 4. Did you use one automatic Expert Team for Photo Helper?

> “We orchestrated our own staged expert team rather than presenting it as one automatic run. HY3 worked with the Mobile Application Developer expert, Kimi-K3 worked with the UI Designer expert, and HY3 returned with the Software Workshop expert. The handoffs were intentional: investigate, challenge the experience, implement, and verify.”

### 5. Why did you choose those particular combinations?

> “We matched the model and expert to the work. HY3 handled repository-scale technical investigation and the final implementation-and-device loop. Kimi-K3 was paired with the UI Designer expert for the high-leverage interaction review. That gave us both engineering depth and an independent product perspective without asking one context to judge all of its own work.”

### 6. Why was WorkBuddy used after the core app was functional?

> “That was deliberate model allocation, not an afterthought. Early development required many fast build-and-debug loops. Once the full journey existed, WorkBuddy could inspect the complete workspace, screenshots, tests and intended flow as an independent product-quality team. Its deeper expert review had more leverage because it could evaluate a real end-to-end product.”

### 7. What did WorkBuddy concretely change?

> “It changed user-visible behavior, not only documentation. The UI review aligned the listening guidance with the compact square Stop control, and Reset remained available whenever an adjustment could still be reversed. The final WorkBuddy pass implemented the selected changes, rebuilt the APK and checked the result on a physical OnePlus phone.”

### 8. How did you stop multiple experts from producing contradictory advice?

> “We gave every stage a bounded job and a clear handoff. The first pass had to produce evidence-backed findings before editing. The design pass concentrated on comprehension and recovery. The final pass implemented only selected changes and verified them on the device. The experts supplied perspectives; the team retained one product direction.”

### 9. What remained the human role?

> “The human role was product judgment. We defined the voice-first goal, the safety and privacy boundaries, and which user problems mattered. WorkBuddy investigated and proposed changes, but recommendations that added clutter or weakened the interaction were discarded. The final experience remained a coherent product rather than an average of several agent opinions.”

### 10. Why not simply ask one strong model to do everything?

> “A single long context tends to carry its own assumptions from analysis into implementation and then into self-review. Separate expert passes create useful disagreement. One agent can understand the system, another can challenge the interaction, and a final pass can implement and verify against the original goal. The value is structured review, not merely more tokens.”

### 11. How is this different from using a normal chatbot?

> “A chatbot mainly gives advice. WorkBuddy operates against the project workspace and can plan, inspect files, make changes and return deliverables for review. Its Experts add domain-specific framing, while the team workflow divides and cross-checks work. For us, it connected product review directly to implementation and real-device verification.”

### 12. Are Expert Teams appropriate for every task?

> “No. A tiny, obvious edit does not need a team. Expert Teams are most valuable when a task crosses disciplines, when independent review matters, or when the cost of a missed issue is high. They add coordination time and model usage, so we reserved them for product-quality decisions where multiple perspectives could change the result.”

### 13. Could another team reproduce your workflow?

> “Yes. Start with a functional product and a clear handover containing the intended journey, project files and non-negotiable constraints. Run an audit-only expert pass, rank findings by impact, select a small set, then use a separate implementation-and-verification pass. The reusable pattern is evidence, bounded roles and explicit handoffs.”

### 14. What evidence remains if the original WorkBuddy task history is unavailable?

> “I would be transparent that the live task history is no longer available. The repository still retains the WorkBuddy usage narrative, the named workflow and the corresponding user-visible behavior. I would not invent screenshots or claim evidence we no longer have; I would show the shipped interaction and explain the exact specialist handoffs.”

## If a judge asks for a ten-second answer

> “WorkBuddy let us combine different models with specialized expert roles. We used three bounded passes—technical investigation, UI/UX review, then implementation and physical-device verification—so the agents could challenge and check each other instead of one model reviewing its own work.”

## Claims to avoid

- Do not say an Expert is a separately trained human-equivalent model; official documentation describes domain knowledge and tailored prompts.
- Do not say your workflow was one automatic Expert Team run; it was a staged sequence of model-plus-expert pairings.
- Do not call Kimi-K3 or HY3 “the best” or “SOTA.” Available models can vary by product version, account and service availability.
- Do not repeat test-count claims. Physical-device verification and concrete product changes are the stronger story.
- Do not claim the missing WorkBuddy task history or screenshots still exist.

## Official research sources

- [WorkBuddy Expert Center](https://www.workbuddy.ai/docs/workbuddy/From-Beginner-to-Expert-Guide/Function-Description/Expert-Center) — experts, categories, tailored prompts and summoning workflow.
- [WorkBuddy Explore: Skill vs. Expert](https://www.workbuddy.ai/docs/workbuddy/From-Beginner-to-Expert-Guide/Function-Description/Explore#explore-vs-skill-vs-expert) — Skill as capability, Expert as the specialist who helps.
- [WorkBuddy Tips & Tricks](https://www.workbuddy.ai/docs/workbuddy/From-Beginner-to-Expert-Guide/Efficient-Tips#_4-use-experts-for-specialized-tasks) — specialized experts, parallel tasks and reviewing changes.
- [WorkBuddy Model Configuration](https://www.workbuddy.ai/docs/workbuddy/From-Beginner-to-Expert-Guide/Function-Description/Model) — model selection is separate; Auto Mode and model availability.
- [WorkBuddy official homepage](https://www.workbuddy.ai/) — expert agents can plan, execute and run tasks in parallel.
- [Tencent Cloud’s Expert Teams description](https://x.com/tencentcloud/status/2074073810262011914) — built-in expert teams divide work, cross-check and deliver results to the workspace.
