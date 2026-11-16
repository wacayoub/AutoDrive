# Accueil AutoDrive 2.0

Nouvel écran d’accueil d’AutoDrive pour l’Incarlink (800 × 480), demandé le 16 novembre 2026 : un lanceur neuf et épuré au style de Radio Maroc & Coran. `HomeActivity` devient l’activité `HOME` et l’icône du lanceur. L’ancien tableau de bord web (`WebDashboardActivity`) n’est plus l’accueil ; sa partie téléphone sera reprise dans l’accueil à l’étape suivante.

## Contenu

- **Heure, date, météo, salat** : grande horloge et date en français. La météo et la prochaine prière de la ville choisie proviennent de `LocalInfoFeed`, partagé avec la radio.
- **Média** : ce qui joue dans n’importe quelle application (Radio Maroc & Coran, YouTube, Spotify…), avec logo ou pochette, une lueur de sa couleur, et ⏮ ⏯ ⏭. Un appui ouvre l’application. Cela passe par `MediaSessions` en mode `followActive`, avec l’accès aux notifications d’Android que l’utilisateur accorde une fois. Sans média, un bouton ouvre Radio Maroc & Coran.
- **Vitesse** : vitesse GPS en km/h (`GpsMonitor`), après autorisation de la localisation précise.
- **Maps** : ouvre Google Maps en plein écran.
- **Favoris** : six applications au plus, réglées depuis « Modifier » ou par appui long dans la grille. Au départ : Radio Maroc & Coran, YouTube, Waze, Spotify, YouTube Music et Chrome, s’ils sont installés.
- **Dock** : Accueil, Téléphone (application de téléphone du boîtier pour l’instant), Applis (grille de toutes les applications), Réglages (Jour / Nuit / Système, ville, réglages Android).
- **Style** : palettes Jour et Nuit (`home_colors.xml`), cartes en verre et fond animé en Jour (`AmbientBackdrop`). Les palettes, `RadioMotion`, `LocalInfoFeed`, `RadioLocalInfo`, `WeatherIcon` et `SystemNight` sont désormais dans `shared-info`, compilé par les deux applications.

## Bulle de la radio

Radio Maroc & Coran affiche sa bulle flottante quand son écran n’est pas au premier plan. Sur l’accueil, qui montre déjà la radio, `HomeActivity` envoie `com.autodrive.radiomaroc.HOME_VISIBLE` (`visible` vrai ou faux) à l’application radio. `PlaybackService` masque la bulle tant que l’accueil est devant et la rétablit dès qu’une autre application passe au premier plan.

## Maps et YouTube dans l’accueil

L’exigence d’[AGENTS.md](../AGENTS.md) reste non satisfaite : héberger les applications Maps et YouTube installées dans deux panneaux interactifs de l’accueil est bloqué sur ce firmware ([audit](NATIVE-PANELS-AUDIT.md)). Le bloc Maps de l’accueil 2.0 est un raccourci demandé par le propriétaire. Il ne constitue pas cette intégration et n’est pas présenté comme une solution de remplacement.

## Vérifications

Sur l’émulateur Android 13 en 800 × 480, en Jour : l’accueil, le bloc Média avec Radio Maroc & Coran (MFM Radio puis HIT RADIO), la grille d’applications et les réglages ont été affichés. La bulle de la radio disparaît sur l’accueil et revient au-dessus d’une autre application. Les tests unitaires des deux applications ont réussi, et lint a signalé 0 erreur. Le boîtier reste à essayer.
