# Applications Android installées — 1.14.0

Le mode par défaut est **Applications Android · plein écran**, également pour une mise à jour depuis une version web. AutoDrive n’installe pas Google Maps ni YouTube pour l’utilisateur. Aucun site, WebView, image de carte ou vidéo n’est chargé par les panneaux Maps/YouTube dans ce mode. Les cartes d’accueil sont des accès aux applications, explicitement libellés plein écran.

## Utilisation

- Panneau Google Maps → Ouvrir Google Maps : lancement explicite du package officiel `com.google.android.apps.maps`. La recherche, le choix de destination, les couches, le zoom et la navigation sont ceux de l’application réelle, dans son écran. AutoDrive ne les reproduit pas dans un widget.
- Settings → Application YouTube : sélectionner l’application déjà installée. Media → YouTube l’ouvre directement. Le panneau permet de l’ouvrir à nouveau ou de changer le choix. Le composant choisi est persistant ; une désinstallation ne provoque pas un changement silencieux d’application.
- Retour/Home : comportement Android de l’application externe et du lanceur choisi. AutoDrive ne lance aucune application externe depuis onResume ou onNewIntent. Home retourne donc au lanceur sans boucle de relance.
- Google Maps absent/désactivé : état explicite, bouton Google Play dirigé uniquement vers le Play Store officiel si présent. YouTube absent : choix d’une application ou Play Store. Aucun téléchargement ni installation automatique d’une application tierce.
- Settings → Affichage Maps / YouTube : sélection explicite Sites web pour retrouver les deux panneaux web. Changer de mode recrée l’activité et détruit les anciens panneaux web. Les sessions privées des sites restent dans leur profil existant, mais ne sont pas transférées aux applications.

Les comptes et préférences Maps/YouTube sont gérés par leurs applications. AutoDrive n’extrait pas leurs cookies, jetons ou mots de passe. La conservation réelle de la connexion dépend aussi des applications choisies et n’a pas été testée avec le compte Google du propriétaire.

## Intégration vérifiée et limites

Les intents Google Maps sont une ouverture de l’application, pas une intégration de son activité dans un widget. [Documentation Google Maps Intents Android](https://developer.android.com/guide/components/google-maps-intents).

L’hébergement inter-applications nécessite la coopération de l’application embarquée et un système compatible. Le diagnostic transmis du Samsung n’autorise pas cet hébergement pour Maps ; il ne prouve pas la situation du boîtier Incarlink. Aucune interface constructeur supportée n’a été validée sur ce dernier. [Documentation Android Activity embedding](https://developer.android.com/develop/ui/views/layout/activity-embedding#cross-application-embedding).

Aucun SDK Maps/Navigation n’est ajouté ; les conditions d’éligibilité de son intégration embarquée ne sont donc pas présentées comme résolues. Aucun partage d’écran forcé, API cachée, privilège système ou modification de firmware n’est utilisé.

La radio reste native. Lancer une application externe applique le cycle de vie audio existant : AutoDrive ferme sa lecture radio en quittant l’activité. Les limitations Bluetooth de la version 1.13 restent applicables.

Les résultats de compilation, instrumentation, captures et publication sont consignés dans [TEST-RESULTS](TEST-RESULTS.md). Une ouverture d’Horloge comme application de test prouve le transfert Android vers le composant choisi et le retour sans boucle ; elle ne prouve ni lecture YouTube ni guidage Google Maps.
