# HANDOFF — relais entre A et B

> Règle : à la fin de chaque session, ajouter 3 lignes EN HAUT. Pull avant de commencer, push avant de partir.

```
### JJ/MM HHhMM — <A|B>
- Fait :
- Prochain :
- Cassé / à savoir :
```

---

### 18/11 — Plateforme complète générée (par Claude)
- Fait : 7 services (rail-bridge, air-bridge, carshare, pricing, reservations/saga, notification, gateway), compose override (RabbitMQ + Jaeger + OTel), demo.sh, scenario.http, ADR, tests saga + pricing.
- Prochain : `docker compose up --build`, corriger les erreurs de build éventuelles, lancer `./demo/demo.sh`, vérifier les traces dans Jaeger.
- Cassé / à savoir : le code n'a PAS pu être compilé là où il a été généré (pas d'accès à Maven Central) -> premières erreurs de build possibles. Logique saga et règles de prix testées séparément.
