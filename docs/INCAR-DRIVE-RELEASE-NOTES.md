Incar Drive 1.0.18 — écran d’accueil pour l’Incarlink (Android 11 à 13, conçu pour 800 × 480).

**Mettre à jour** : Réglages › Mises à jour, ou la proposition automatique.

**Changements**
- Carte OpenStreetMap améliorée : elle tourne avec la voiture, la route devant toi en haut (touche la boussole pour remettre le nord en haut) ; elle glisse en douceur au lieu de sauter à chaque position ; le zoom s’adapte à la vitesse (plus près en ville, plus large sur route rapide) ; tu peux la déplacer au doigt pour regarder autour, puis « Recentrer » (elle revient seule après 15 secondes) ; le nom de la rue où tu roules s’affiche en bas.
- Ciel réel derrière la route : le fond de la carte de conduite suit le soleil et la météo du jour. Le soleil se lève à gauche, monte, puis se couche à droite, avec les couleurs de l’aube, du plein jour, du couchant et de la nuit étoilée ; des nuages passent quand le ciel est couvert, la pluie ou la neige tombent quand il y en a. La route, les phares et les chiffres passent en mode nuit quand il fait nuit dehors, quel que soit le thème choisi.

Comprend aussi la 1.0.17 :
- Icônes d’état refaites : le Wi-Fi en éventail qui se remplit avec le signal, suivi de sa génération dans un badge quand le boîtier la donne (4, 5, 6, 6E, 7) ; le réseau mobile en barres arrondies avec un badge « 5G », « 5G+ », « 4G+ » ou « 4G » (le 5G est reconnu aussi quand il passe par une ancre 4G) ; une icône de partage de connexion quand le boîtier sert de point d’accès. Réglages rapides : nouvelle tuile « Partage », qui ouvre les réglages réseau d’Android. Le badge réseau demande l’autorisation « Téléphone » (touche les barres).
- Barre du haut : le logo de la voiture et le badge n’y sont plus, pour laisser tout le milieu aux notifications (alertes de la route, prochain virage, radar, prière), qui peuvent tenir sur deux lignes. Le logo reste sur la voiture du compteur Route et dans la scène d’accueil ; le badge HYBRID est retiré.
- Alertes de la route d’après Google Maps (ou Waze) : quand l’appli de navigation écrit une alerte dans une de ses notifications (accident, route fermée, danger, ralentissement, travaux, contrôle de vitesse…), elle s’affiche dans la pastille en haut de l’accueil, en rouge pour un accident, une route fermée ou un danger, en orange pour le reste, avec un bip. Elle disparaît quand l’appli la retire. Incar Drive ne montre que ce que l’appli écrit ; il ne cherche pas les incidents ailleurs.
- Barre de A à B aux couleurs du trafic de Google Maps : la partie déjà parcourue garde, tronçon par tronçon, la couleur que Maps donnait à ce moment-là (vert, orange, rouge) ; la partie à venir prend, en plus pâle, la couleur du moment. Ce n’est pas le détail des bouchons à venir, que Maps ne transmet pas, et il faut que la notification de Maps contienne une couleur ; sinon la barre reste bleue.

Et la 1.0.16 :
- La ligne sous la route devient l’itinéraire de A à B pendant un guidage : un point bleu au départ, un point vert à la destination, la partie parcourue en dégradé du bleu au vert, et un point blanc pour la voiture. Elle avance avec la distance restante que donne Google Maps.
- Pied de la carte de conduite refait : le trajet tient sur une ligne fine à son propre style : durée, distance et vitesse moyenne, chacune avec son icône colorée. Pendant un guidage, trois cases au-dessus donnent l’heure d’arrivée (à la couleur du trafic de Maps), le temps restant et la distance restante ; un toucher sur le trajet ouvre Maps. Sans guidage : un bouton bleu pour naviguer, puis Maison, Travail, tes lieux et les recherches.
- Voiture redessinée, plus moderne et un peu plus petite : basse et large, toit vitré qui descend jusqu’au coffre, une fine barre de feux sur toute la largeur (plus vive au freinage, clignotant orange du côté du virage), diffuseur sombre. Elle garde la couleur choisie et ton logo sous la barre de feux.
- Thème jour de la carte de conduite refait : ciel bleu qui s’éclaircit vers l’horizon, sol clair de chaque côté, route sombre et nette avec ses marquages blancs, bordures d’un bleu plus soutenu. La ligne sous la route est en dégradé dans les deux thèmes.
- Couleurs du trafic d’après Google Maps uniquement : la route et l’heure d’arrivée ne passent au vert, à l’orange ou au rouge que si Google Maps donne cette couleur dans sa notification de guidage. Les estimations d’Incar Drive (vitesse moyenne, rythme du trajet) sont retirées ; sans couleur de Maps, la route reste bleue et l’heure d’arrivée reste dans la couleur du texte.
- Animation de la route modernisée : en guidage tout droit, des chevrons lumineux remontent ta voie vers l’horizon ; les flèches de virage, de rond-point et de demi-tour sont éclairées (halo, dégradé, liseré) ; le passage d’une scène à l’autre se fait en fondu au lieu d’un saut ; l’animation suit la cadence de l’écran quand le boîtier le permet.
- Maison, Travail et tes lieux sans quitter l’accueil : un toucher lance le guidage, et l’accueil revient tout seul deux secondes après son démarrage. Google Maps (ou Waze) doit s’ouvrir un instant pour démarrer le guidage : Android ne permet pas de le lancer en arrière-plan. Il faut l’autorisation « Superposition aux autres applis » d’Incar Drive.
- Carte de conduite : pied plus compact (icône, titre, boutons Maison et Travail) pour laisser plus de hauteur à la route ; une ligne colorée sépare la route du pied, avec la ligne du trajet juste dessous, et se remplit sur les 500 derniers mètres avant un virage pendant un guidage ; la ligne du trajet (durée, distance, vitesse moyenne) comme la deuxième ligne du pied sont plus grandes et plus lisibles.

Et la 1.0.15 :
- Guidage en direct : entre deux mises à jour de Google Maps (qui avance par paliers et avec un peu de retard), la distance au virage est décomptée par Incar Drive avec la vitesse de la voiture, image par image, et se recale à chaque nouvelle valeur de Maps. Le virage se dessine donc dès qu’il est proche, sans attendre Maps.
- Sens du virage lu dans la flèche de Maps : sur certains boîtiers, la notification de Maps ne contient aucun mot, seulement la distance et l’image de la flèche. Incar Drive lit maintenant la forme de cette flèche (gauche, droite, tout droit, demi-tour) ; avant, la carte restait sur « tout droit ».
- Trafic : un arrêt à un feu ou avant un virage ne colore plus la route en rouge ; seul un vrai bouchon (à l’arrêt la plupart du temps, en avançant par à-coups) le fait.
- La notification de guidage est lue plus vite.
- Guidage affiché une seule fois : avec le compteur Route, le virage et sa distance sont sur la route. Le pied de la carte ne les répète plus : il donne l’heure d’arrivée (en couleur selon le trafic), le temps et la distance restants. La carte de droite et le cadre Maps n’affichent plus le virage une deuxième fois.

Et la 1.0.14 :
- Une seule carte pour conduire : le compteur et la carte de navigation ne font plus qu’un. En haut, la vitesse, la route et le guidage ; au pied de la même carte, « Naviguer », Maison, Travail et tes lieux, ou pendant un guidage la distance, la consigne complète, le temps restant et l’heure d’arrivée en couleur.
- Compteur « Route » et guidage fusionnés : pendant un guidage Google Maps ou Waze, la route du compteur montre la suite. Tout droit : une flèche bleue sur ta voie. Virage à moins de 400 m : la route tourne à gauche ou à droite, avec la flèche, des chevrons, le nom de la rue sur un panneau vert et le clignotant de la voiture. Rond-point : l’anneau et la flèche vers la sortie comptée. Demi-tour et arrivée (drapeau) aussi. En haut à gauche : la flèche de Maps, la distance et un mot (« à droite », « sortie 2 »…) ; en bas : une barre qui se remplit sur les 500 derniers mètres, puis le temps restant et l’heure d’arrivée.
- Radar sur la route : le radar et sa limite apparaissent sur les bas-côtés et se rapprochent, avec une zone rouge sur la chaussée.
- Couleur de la voiture : Réglages › Compteur de vitesse (blanc, gris, noir, rouge, bleu). Phares allumés la nuit, ciel du moment derrière l’horizon.
- Couleurs du trafic sur le compteur Route : les bords de la route passent au vert (fluide), à l’orange (ralenti) ou au rouge (bouchon), d’après ta vitesse moyenne sur la dernière minute comparée à la limite de la route. C’est une estimation du trafic où tu roules, sans service de trafic ni compte.
- Heure d’arrivée en couleur : le temps restant et l’heure d’arrivée sont verts, orange ou rouges comme dans Google Maps. Incar Drive reprend la couleur de Maps quand sa notification la donne ; sinon il l’estime d’après le rythme du trajet restant (distance ÷ temps).
- Le sens du virage est lu dans la consigne de Maps (français, anglais, arabe). La route dessinée indique la direction, pas la forme réelle de la route ; une consigne non reconnue est montrée comme « tout droit ».
- Cadre Google Maps pendant le guidage : le haut du cadre affiche le prochain virage en grand (flèche, distance, consigne, temps restant et heure d’arrivée), juste au-dessus de la petite fenêtre de Maps. La carte de navigation du milieu garde alors Maison, Travail et tes lieux.
- Bulle de guidage : elle ne s’affiche plus au-dessus de Google Maps ou de Waze eux-mêmes, où elle répétait leur écran ; elle reste au-dessus des autres applis ouvertes depuis l’accueil. Elle n’écrit plus la distance deux fois.

Et la 1.0.13 :
- Îlot en direct au milieu de la barre du haut : une pastille qui remplace le logo le temps d’une alerte, puis s’efface. En rouge au-dessus de la limite (« 56 km/h · limite 40 ») et à l’approche d’un radar ; en bleu pour le prochain virage quand la carte de navigation n’est pas à l’écran (page 2, panneau ouvert) ; en vert pour une prière dans moins de 15 minutes.
- Mode calme : Réglages rapides › « Calme ». Écran noir avec seulement l’heure, la vitesse en grand et le prochain virage, pour rouler de nuit. Un toucher ramène l’accueil.
- Assistant vocal : Réglages rapides › « Assistant » ouvre l’assistant du boîtier (Google Assistant s’il est installé).
- Scène d’accueil au démarrage : le logo de la voiture et le salut du moment, qui s’effacent sur l’accueil.
- Cartes légèrement plus claires en haut, comme du verre éclairé.

Et la 1.0.12 :
- « Appli Maps » à droite de l’accueil (nouveau choix) : un cadre fait pour la petite fenêtre de l’application Google Maps, la seule forme sous laquelle Android 13 laisse l’appli apparaître sur un écran d’accueil. Pendant un guidage, le cadre descend jusqu’en bas et la fenêtre de Maps s’y pose, avec l’itinéraire et les consignes de Maps.
- Retour automatique à l’accueil : avec « Appli Maps », environ 5 secondes après le début d’un guidage Google Maps, l’accueil revient tout seul et Maps passe en petite fenêtre, sans appuyer sur Accueil. Il faut l’autorisation « Image dans l’image » de Maps et « Superposition aux autres applis » d’Incar Drive. Waze, qui n’a pas de petite fenêtre, reste à l’écran.

Et la 1.0.11 :
- Thème « Système » fiable au démarrage : l’accueil revérifie jour ou nuit chaque minute et bascule tout seul. Avant, il décidait une seule fois à l’allumage ; si l’heure du boîtier était encore fausse à ce moment-là, il pouvait rester en sombre en plein jour (ou l’inverse). Pour un thème toujours identique, choisis « Jour » ou « Nuit » dans Réglages › Thème.

Et la 1.0.10 :
- Thème nuit : les cartes Maps web et Waze passent en sombre, comme la carte d’Incar Drive ; le trafic garde ses couleurs.
- Google Maps web à droite de l’accueil (nouveau choix) : le site de Google Maps avec son trafic en direct (lignes vertes, orange, rouges), gratuit, sans clé ni compte. Il est en direct : Incar Drive donne la position du boîtier au site de Google Maps, qui affiche ton point bleu et suit la voiture en continu. Si tu déplaces la carte au doigt, « Centrer » relance le suivi ; « Ouvrir Maps » lance l’appli pour le guidage. C’est le site web, pas l’appli : pas de guidage dans la carte ; il demande Internet et Android System WebView.
- Carte Waze à droite de l’accueil (nouveau choix) : la carte web officielle de Waze, gratuite et sans compte, avec son trafic et les signalements de ses conducteurs. Elle se recentre sur la voiture tous les 400 m ; « Centrer » le fait tout de suite, « Ouvrir Waze » lance l’appli. Elle ne montre ni itinéraire ni guidage, et demande Internet et le composant Android System WebView. Réglages › Navigation › « À droite de l’accueil » : Maps web, Waze, Carte, Widget ou Favoris.

Et la 1.0.9 :
- Carte Incar Drive à droite de l’accueil, gratuite et sans compte : la carte OpenStreetMap autour de la voiture, avec ta position et ta direction, les radars, le zoom + / −, et la consigne de Google Maps ou Waze par-dessus pendant un guidage. Un toucher ouvre la navigation. Ce n’est pas Google Maps : pas de ligne d’itinéraire ; il faut Internet pour les zones jamais vues, les autres restent en mémoire. Réglages › Navigation › « À droite de l’accueil » : Carte, Widget Maps ou Favoris.
- Trafic en direct sur la carte (option) : les bouchons en couleurs, fournis par TomTom avec ta propre clé gratuite (200 000 tuiles par mois, sans carte bancaire). Réglages › Navigation › « Ajouter le trafic en direct sur la carte » explique comment l’obtenir. Sans clé, la carte reste sans trafic.
- Thème jour : le logo de la voiture et le badge sont posés sur une plaque sombre, pour rester visibles sur le ciel clair.
- Compteur Route : la ligne du trajet reste lisible en thème jour.
- Panneaux (Applis, Réglages, Téléphone) : plus de texte de la barre du haut qui dépasse au coin.

Et la 1.0.8 :
- Logo Toyota argent + HYBRID : Réglages › Écran d’accueil › « Logo Toyota argent + HYBRID ». L’emblème Toyota en chrome argent (récupéré sur Wikimedia Commons) et un badge HYBRID chromé avec sa vague bleue, dessiné par Incar Drive, au milieu de la barre du haut. Rien n’est inclus dans l’appli.
- Cadre Google Maps jusqu’en bas : pendant un guidage, la carte de droite descend jusqu’au bas de l’écran et la barre du bas se raccourcit (Accueil, Téléphone, Applis, Réglages), pour que la petite fenêtre de Maps, qu’Android place en bas à droite, se pose dans le cadre et non sur la barre. Tout revient à la fin du guidage ou en page 2.
- Carte de navigation : la distance n’est plus répétée sur la seconde ligne, même quand Google Maps l’écrit avec des espaces spéciaux (« 8.5 km »).

Et la 1.0.7 :
- Petite fenêtre Google Maps et carte ensemble : pendant un guidage, la carte de droite laisse place à un cadre sobre où la petite fenêtre de Maps vient se poser (Android la place en bas à droite ; pince-la pour l’agrandir, glisse-la pour la caler). Sans petite fenêtre, le cadre propose « Ouvrir Maps ». Le widget revient à la fin du guidage.
- Carte de navigation : la seconde ligne donne l’instruction ou la rue, au lieu de répéter la distance (« 11 km · Continuer tout droit »).

Et la 1.0.6 :
- La partie droite de l’accueil est pour la carte : le widget Google Maps « Trafic à proximité » (ou Waze), ajouté d’une touche la première fois. Les favoris passent en page 2 ; les applis du quotidien sont dans la barre du bas. Réglages › Navigation › Carte sur l’accueil pour revenir aux favoris.
- Nouveau compteur « Route » : la vitesse en grand au-dessus de la route vue de derrière la voiture, sur toute la carte : lignes des voies et balises au bord de la route qui défilent selon la vitesse, lueur à l’horizon, une berline hybride vue de l’arrière dans les lignes de la Corolla Hybrid (feux qui s’allument plus fort au freinage, ton logo sur le coffre), comme sur les tableaux de bord numériques récents (Toyota…). Le bord de route et le chiffre passent en rouge au-dessus de la limite. Réglages › Compteur de vitesse pour changer de style.
- Quatre autres compteurs, proches des tableaux de bord numériques récents (dessins originaux, sans logo) : Électrique (grand chiffre et barre recharge / puissance), Angulaire (cadran à facettes orange), Virtuel (grand cadran rond, aiguille rouge), Élégant (anneau argent et arc turquoise). La barre recharge / puissance suit la vitesse GPS : le boîtier ne lit pas les données de la voiture.
- Logo de ta voiture en haut à gauche : Réglages › Écran d’accueil › Logo de la voiture, puis choisis une image (PNG transparent conseillé) sur le boîtier ; il s’affiche aussi sur le coffre de la voiture du compteur Route. Un second badge (ex. « Hybrid ») peut se mettre à côté du logo. Logo et badge sont au milieu de la barre du haut, réduits avec soin pour des bords nets. Incar Drive n’embarque aucun logo de marque.
- Favoris en logos seuls, sans cadre ; les places libres montrent un simple « + ».
- Reprise au démarrage : quand le boîtier démarre avec la voiture, Incar Drive relance la dernière appli écoutée, sans appuyer sur Lecture (Réglages › Média, activée par défaut). Pour Radio Maroc & Coran, il faut sa version 2.5.49.
- Barre du bas : Accueil, Téléphone, Applis et Réglages plus compacts, et jusqu’à 5 applis en logos à droite (Google Maps, Waze, YouTube, la radio… au départ). « + » pour en ajouter ; appui long sur un logo pour le retirer ; appui long dans Applis pour « Ajouter à la barre du bas ».
- Programme de la radio sur la carte média : l’émission en cours et ses horaires (ex. « 19:03–20:00 · Mawîd li nikach » sur Radio Nationale), ou le titre diffusé par la station ; le nom de la station reste en grand.
- « Écouter … » sur la carte média joue directement la dernière appli (la radio au départ), sans l’ouvrir.

Et la 1.0.5 : bulle de guidage au-dessus des autres applis et aide pour la petite fenêtre Google Maps.

Signée avec la même clé que Radio Maroc & Coran.
