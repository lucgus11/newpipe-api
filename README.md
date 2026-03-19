# newpipe-api

Microservice Kotlin/Ktor utilisant NewPipe Extractor pour LucTube.

## Endpoints

- `GET /health` — Status
- `GET /trending?region=FR` — Vidéos tendances
- `GET /search?q=...` — Recherche
- `GET /streams/:videoId` — Info + streams d'une vidéo

## Déploiement sur Railway

1. Crée un compte sur [railway.app](https://railway.app)
2. New Project → Deploy from GitHub → sélectionne ce repo
3. Railway détecte automatiquement le Dockerfile
4. Copie l'URL générée (ex: `https://newpipe-api-xxx.railway.app`)
5. Ajoute-la dans `api/piped.js` de LucTube comme `NEWPIPE_URL`
