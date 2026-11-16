# Incar Drive

Lanceur (écran d’accueil) pour l’Incarlink en 800 × 480, demandé le 16 novembre 2026 : une application neuve et épurée, distincte d’AutoDrive, au style de Radio Maroc & Coran. Module `incar-drive`, identifiant `com.incardrive.home`, version 1.0.0. `HomeActivity` est l’activité `HOME` et l’icône du lanceur. AutoDrive (`app`) n’est pas modifié.

## Contenu

- **Heure, date, météo, salat** : grande horloge et date en français. La météo et la prochaine prière de la ville choisie proviennent de `LocalInfoFeed`, partagé avec la radio.
- **Média** : ce qui joue dans n’importe quelle application (Radio Maroc & Coran, YouTube, Spotify…), avec le logo ou la pochette, une lueur de sa couleur, et ⏮ ⏯ ⏭. Un appui ouvre l’application. Cela passe par `MediaSessions` en mode `followActive`, avec l’accès aux notifications d’Android, accordé une fois. Sans média, un bouton ouvre Radio Maroc & Coran.
- **Vitesse** : vitesse GPS en km/h (`GpsMonitor`), après autorisation de la localisation précise.
- **Maps** : ouvre Google Maps en plein écran.
- **Favoris** : six applications au plus, réglées depuis « Modifier » ou par appui long dans la grille. Au départ : Radio Maroc & Coran, YouTube, Waze, Spotify, YouTube Music et Chrome, s’ils sont installés.
- **Dock** : Accueil, Téléphone (application Bluetooth de l’Incarlink, sinon le composeur), Applis (grille de toutes les applications) et Réglages (Jour / Nuit / Système, couleur du tableau de bord, ville, réglages Android).
- **Style** : palettes Jour et Nuit (`home_colors.xml`), panneaux presque opaques et fond `CockpitBackdrop`. `RadioMotion`, `LocalInfoFeed`, `RadioLocalInfo`, `WeatherIcon` et `SystemNight` se trouvent dans `shared-info`, compilé par Incar Drive et Radio Maroc & Coran. `MediaSessions`, `GpsMonitor` et `SpeedFilter` sont des copies adaptées de celles d’AutoDrive, compatibles avec Android 11.

## Thème

L’accueil est l’interface principale du boîtier. Son thème « cockpit » lui donne une identité propre, distincte de Radio Maroc & Coran (16 novembre 2026) :

- **Couleurs** : graphite la nuit, argent le jour, et une seule couleur d’accent choisie dans Réglages : Neutre (par défaut), Ambre, Cyan ou Rouge. Elle colore l’arc de vitesse, le bouton lecture, la carte Maps, le dock et les légendes. Neutre est blanc sur graphite la nuit et graphite sur argent le jour ; il ne teinte ni la carte média selon le logo ni les favoris selon leur icône, et la carte Maps y devient un panneau comme les autres.

- **Bandeau** : salutation selon l’heure (« Bonjour », « Bon après-midi », « Bonsoir », « Bonne nuit »), horloge condensée fine en 44 sp et date. Les légendes sont en petites capitales espacées. La météo affiche la température et l’état du ciel, la salat la prochaine prière et un compte à rebours (« dans 25 min »).
- **Média** : la carte entière se teinte d’un dégradé de la couleur du logo, en fondu d’une station à l’autre, avec un logo plus grand, ombré, et l’égaliseur animé pendant la lecture.
- **Vitesse** : `SpeedGauge`, un cadran de 240° dont l’arc dégradé dans la couleur d’accent, gradué tous les 10 km/h, glisse vers la vitesse GPS. L’échelle passe à 200, puis à 260 km/h, pour ne jamais buter.
- **Maps** : carte au dégradé de la couleur d’accent, avec une flèche de navigation, « Naviguer » et un chevron.
- **Favoris** : trois rangées de deux tuiles, chacune teintée de la couleur dominante de son icône ; les places libres montrent un « + » en pointillés qui ouvre le choix des favoris.
- **Dock** : barre flottante, avec la page en cours sur fond d’accent. Les boutons sont des carrés arrondis, comme des touches de tableau de bord. Les pages Applis et Réglages glissent au-dessus de l’accueil.
- **Fond** : `CockpitBackdrop`, statique : un dégradé, une lueur de la couleur d’accent en haut à droite et une grille de route en perspective vers l’horizon.

## Bulle de la radio

Radio Maroc & Coran affiche sa bulle flottante quand son écran n’est pas au premier plan. L’accueil montre déjà la radio : `HomeActivity` envoie donc `com.autodrive.radiomaroc.HOME_VISIBLE` (`visible` vrai ou faux) à l’application radio. `PlaybackService` masque la bulle tant que l’accueil est au premier plan, et la rétablit dès qu’une autre application passe devant.

## Signature

Incar Drive est signé avec la clé de Radio Maroc & Coran (SHA-256 `b947b7ff…`) par `.release-signing/build-incardrive-release.ps1`, qui reprend le mot de passe enregistré pour la radio.

## Maps et YouTube dans l’accueil

L’exigence d’[AGENTS.md](../AGENTS.md) reste non satisfaite : héberger les applications Maps et YouTube installées dans deux panneaux interactifs de l’accueil est bloqué sur ce firmware ([audit](NATIVE-PANELS-AUDIT.md)). Le bloc Maps est un raccourci demandé par le propriétaire. Il ne constitue pas cette intégration et n’est pas présenté comme une solution de remplacement.

## Vérifications

Sur l’émulateur Android 13 en 800 × 480 :

- l’accueil s’est affiché en Jour et en Nuit, avec le bloc Média sur Radio Maroc & Coran (MFM Radio, puis HIT RADIO), la grille d’applications et les réglages ;
- la bulle de la radio a disparu sur l’accueil et est revenue au-dessus d’une autre application ;
- l’APK signé a démarré comme accueil, et son bouton « Autoriser l’accès » a ouvert le réglage d’accès aux notifications d’Incar Drive.

Lint a signalé 0 erreur. Le boîtier reste à essayer.
