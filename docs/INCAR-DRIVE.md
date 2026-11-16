# Incar Drive

Lanceur (écran d’accueil) pour l’Incarlink en 800 × 480, demandé le 16 novembre 2026 : une application neuve et épurée, distincte d’AutoDrive, au style de Radio Maroc & Coran. Module `incar-drive`, identifiant `com.incardrive.home`, version 1.0.0. `HomeActivity` est l’activité `HOME` et l’icône du lanceur. AutoDrive (`app`) n’est pas modifié.

## Contenu

- **Heure, date, météo, salat** : grande horloge et date en français. La météo et la prochaine prière de la ville choisie proviennent de `LocalInfoFeed`, partagé avec la radio.
- **Média** : ce qui joue dans n’importe quelle application (Radio Maroc & Coran, YouTube, Spotify…), avec le logo ou la pochette, une lueur de sa couleur, et ⏮ ⏯ ⏭. Un appui ouvre l’application. Cela passe par `MediaSessions` en mode `followActive`, avec l’accès aux notifications d’Android, accordé une fois. Sans média, un bouton ouvre Radio Maroc & Coran.
- **Vitesse** : vitesse GPS en km/h (`GpsMonitor`), après autorisation de la localisation précise. Le cadran affiche aussi le cap (N, NE… et degrés, en roulant), l’altitude GPS approximative (au-dessus de l’ellipsoïde sur Android 13, donc quelques dizaines de mètres au-dessus du niveau de la mer) et le résumé du trajet. Un appui ouvre la page **Trajet** : distance, temps de conduite, moyenne, maximum, heure de départ, remise à zéro, et l’**alerte de vitesse** (Route, Non, 60, 80, 100, 120) qui met l’arc en rouge et émet un bip au dépassement. **Route**, le réglage par défaut, suit la limite légale de la route : `RoadLimits` demande au service public Overpass d’OpenStreetMap les routes à 800 m autour de la voiture (la position seule est envoyée, sans identifiant), les rapproche de chaque position GPS et du cap, et recommence après 450 m. Elle s’affiche dans un panneau rond en haut à droite du compteur. Les routes sans `maxspeed` dans la carte ne donnent pas de limite (pas d’estimation), sauf les valeurs marocaines par défaut `MA:urban` (60), `MA:rural` (100) et `MA:motorway` (120). Les serveurs publics sont parfois saturés : le 16 novembre 2026, la première réponse a mis plus de trois minutes sur l’émulateur (puis 80 km/h trouvé sur l’Autoroute urbaine de Casablanca). Données © contributeurs OpenStreetMap, licence ODbL. `TripMeter` ne compte que les positions précises à 30 m et ouvre un nouveau trajet après deux heures sans rouler.
- **Maps** : « Naviguer » ouvre Google Maps en plein écran. Les boutons **Maison** et **Travail** lancent directement le guidage de Google Maps (`google.navigation:q=`, intent documenté) vers l’adresse enregistrée. Un appui long sur le bouton, ou Réglages › Destinations, permet de saisir l’adresse ou d’enregistrer « Ma position » (dernière position connue). Sans adresse, le bouton est estompé et ouvre la saisie. Ces boutons ouvrent l’application Maps ; ils ne l’intègrent pas dans l’accueil, ce qui reste bloqué (voir plus bas).
- **Favoris** : six applications au plus, réglées depuis « Modifier » ou par appui long dans la grille. Au départ : Radio Maroc & Coran, YouTube, Waze, Spotify, YouTube Music et Chrome, s’ils sont installés.
- **Dock** : Accueil, Téléphone (application Bluetooth de l’Incarlink, sinon le composeur), Applis (grille de toutes les applications) et Réglages (Jour / Nuit / Système, couleur du tableau de bord, ville, réglages Android).
- **Style** : palettes Jour et Nuit (`home_colors.xml`), panneaux presque opaques et fond `CockpitBackdrop`. `RadioMotion`, `LocalInfoFeed`, `RadioLocalInfo`, `WeatherIcon` et `SystemNight` se trouvent dans `shared-info`, compilé par Incar Drive et Radio Maroc & Coran. `MediaSessions`, `GpsMonitor` et `SpeedFilter` sont des copies adaptées de celles d’AutoDrive, compatibles avec Android 11.

- **Téléphone (dock)** : clavier et numéros enregistrés dans Incar Drive (appui long pour modifier ou supprimer ; appui long sur 0 pour « + »). « Appeler » transmet le numéro par la requête Android documentée `ACTION_DIAL` : d’abord à l’appli Bluetooth de l’Incarlink si elle l’accepte, sinon à toute autre appli téléphone. Si aucune ne l’accepte, Incar Drive ouvre l’appli Téléphone de l’Incarlink, copie le numéro et l’affiche pour le composer. Incar Drive ne passe pas l’appel Bluetooth lui-même (réservé à l’appli système). À vérifier sur le boîtier : l’appli `com.carsyso.bluetooth` accepte-t-elle `ACTION_DIAL tel:` ? Les numéros enregistrés apparaissent aussi sur la carte Téléphone de la deuxième page.
- **Appels** : quand l’appli téléphone (celle de l’Incarlink, ou toute notification de catégorie « appel ») affiche un appel, l’accueil montre un bandeau en haut avec l’appelant et les boutons de cette notification : Répondre en vert, Refuser ou Raccrocher en rouge, et les autres (haut-parleur…). Les boutons déclenchent ceux de la notification (`PendingIntent`), via l’accès aux notifications déjà accordé pour la carte média ; Incar Drive ne passe ni ne gère lui-même d’appel Bluetooth, réservé à l’appli système. Le propriétaire a confirmé le 16 novembre 2026 que l’appli Bluetooth de l’Incarlink affiche bien une notification avec Répondre / Refuser ; le format exact de ses boutons reste à vérifier sur le boîtier.
- **Réglages rapides** : bouton à gauche de la météo. Curseurs de luminosité (permission « Modifier les paramètres système », accordée dans l’écran Android) et de volume média ; état du Wi-Fi, du Bluetooth et de la position, chacun ouvrant le panneau Android correspondant (une appli ne peut plus les activer elle-même), et bascule Jour / Nuit.
- **Deuxième page** (glisser vers la gauche ; Home ou Accueil ramène à la première) :
  - **Téléphone** : le téléphone connecté en Bluetooth (profils documentés HEADSET et A2DP, et événements de connexion), les contacts favoris et derniers appels qu’Android connaît, et « Ouvrir Téléphone ». Android ne fournit pas d’API publique pour la batterie du téléphone : elle n’est pas affichée. Sur l’Incarlink, les contacts et appels peuvent rester dans l’appli Bluetooth du constructeur ; la carte le dit alors. Autorisations demandées au premier appui : Bluetooth, contacts, journal d’appels.
  - **Widgets** : jusqu’à trois widgets Android côte à côte, via l’API publique `AppWidgetHost`. La liste vient des applis installées ; Android demande une fois l’autorisation de créer des widgets ; un widget avec écran de configuration l’affiche. « Modifier » permet de les retirer.

## Thème

L’accueil est l’interface principale du boîtier. Son thème « cockpit » lui donne une identité propre, distincte de Radio Maroc & Coran (16 novembre 2026) :

- **Couleurs** : graphite la nuit, argent le jour, et une seule couleur d’accent choisie dans Réglages : Neutre (par défaut), Ambre, Cyan, Rouge, Bleu, Vert, Violet, Rose ou Or, choisie par des pastilles rondes. Elle colore l’arc de vitesse, le bouton lecture, la carte Maps, le dock et les légendes. Neutre est blanc sur graphite la nuit et graphite sur argent le jour ; il ne teinte ni la carte média selon le logo ni les favoris selon leur icône, et la carte Maps y devient un panneau comme les autres.

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
