# BusWidget Android

Application Android native pour les départs de bus à Rouen (réseau Astuce).  
Équivalent de l'app iOS mais pour Android, avec widget écran d'accueil.

## Stack

- **Kotlin + Jetpack Compose** — équivalent SwiftUI
- **Glance (Compose for Widgets)** — équivalent WidgetKit
- **Hilt** — injection de dépendances (équivalent du pattern iOS avec env/config)
- **Retrofit + Moshi** — client HTTP + JSON (équivalent URLSession + JSONDecoder)
- **DataStore Preferences** — persistance des favoris (équivalent UserDefaults/AppGroup)
- **WorkManager** — refresh périodique du widget (équivalent Timeline `.after(...)`)

## Architecture

```
app/src/main/java/com/buswidget/
├── data/
│   ├── api/           → Retrofit API, DTOs, Mappers
│   └── local/         → FavoritesStore (DataStore), modèles domain
├── di/                → Module Hilt (Retrofit, Moshi, OkHttp)
├── ui/
│   ├── search/        → Recherche d'arrêts (SearchScreen + SearchViewModel)
│   ├── favorites/     → Favoris (FavoritesScreen + FavoritesViewModel)
│   ├── departures/    → Départs d'un arrêt (DeparturesScreen + DeparturesViewModel)
│   └── theme/         → Material3 avec Dynamic Color
├── widget/            → Widget Glance (BusGlanceWidget, BusWidgetContent, Worker)
├── MainActivity.kt    → Navigation Compose (BottomBar : Recherche / Favoris)
└── BusWidgetApp.kt    → Application class + WorkManager init
```

## Équivalences iOS → Android

| iOS | Android |
|-----|---------|
| SwiftUI View | Jetpack Compose @Composable |
| WidgetKit / AppIntentTimelineProvider | Glance + WorkManager |
| URLSession / JSONDecoder | Retrofit + Moshi |
| UserDefaults (AppGroup) | DataStore Preferences |
| @EnvironmentObject AppModel | Hilt ViewModel |
| `.task(id:)` + debounce | Flow.debounce() dans ViewModel |
| Pull to refresh | PullToRefreshBox (Material3) |

## Prérequis

- **Android Studio Ladybug** (2024.2.1) ou plus récent
- Android SDK 26+
- Backend BusWidget API lancé (`bun run dev` à la racine du repo)

## Lancer le projet

1. Ouvrir Android Studio
2. Ouvrir le dossier `apps/android/`
3. Laisser Gradle synchroniser les dépendances
4. Lancer sur émulateur ou appareil physique

### URL de l'API

- **Émulateur** : `http://10.0.2.2:3000` (déjà configuré par défaut)
- **Appareil physique** : modifier `API_BASE_URL` dans `app/build.gradle.kts` avec l'IP LAN  
  (obtenue via `bun --cwd apps/api run dev:lan`)

## Widget

1. Lancer l'app et ajouter des arrêts en favoris
2. Aller sur l'écran d'accueil Android → Appui long → Widgets
3. Chercher "BusWidget" et glisser le widget sur l'écran
4. Le widget se rafraîchit automatiquement toutes les ~15-30 min (contrainte système Android)
5. Appuyer sur l'icône refresh dans le widget pour une mise à jour immédiate

## Fonctionnalités

- 🔍 **Recherche** d'arrêts par nom (debounce 250ms)
- ⭐ **Favoris** avec sélection optionnelle de lignes
- 🚌 **Départs** temps réel (RT) et horaires prévus (SCH) avec pull-to-refresh
- 📱 **Widget** écran d'accueil avec les 3 prochains départs
- 🌙 **Dark mode** automatique + Dynamic Color (Android 12+)
