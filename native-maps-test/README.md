# Test séparé de Google Maps Android

Cette application de diagnostic (`com.autodrive.nativeprobe`, Android 13 minimum) essaie l’intégration d’une activité Google Maps avec les API publiques AndroidX WindowManager 1.5.1. Elle ne remplace pas AutoDrive, ne devient pas lanceur HOME et ne charge aucun site web. Aucun accès root, permission privilégiée, API cachée, capture d’écran continue ou injection tactile n’est utilisé.

[Télécharger l’APK expérimental 0.3](https://github.com/wacayoub/AutoDrive/releases/download/native-maps-test-0.3/AutoDrive-Native-Maps-Test-0.3.apk). Cette préversion est séparée de la version stable et ne sera pas proposée comme mise à jour d’AutoDrive. Elle met à jour les diagnostics 0.1 / 0.2 avec le même identifiant et certificat.

## Version 0.3 : contrôler la piste firmware / lanceurs

Le contrôle AndroidX précédent ne couvre pas l’hébergement privilégié d’applications par le constructeur. La 0.3 ajoute `oemHostingAudit` au rapport (schéma 3), sans changer les conditions du bouton d’essai Maps. Elle utilise uniquement les API publiques PackageManager, sur un thread de travail :

- certificat SHA-256 courant de la plateforme et comparaison avec le certificat public observé dans AB Launcher 1.5.5 et DUO 1.7.8 ;
- composant HOME résolu et activités HOME visibles (un composant résolu peut être un sélecteur plutôt qu’un lanceur par défaut) ;
- versions et certificats des paquets ciblés, UID, présence sur l’image système et correspondance avec la signature plateforme ;
- permissions pertinentes demandées, indicateurs de permission accordée et permissions effectivement accordées selon Android. Une permission peut être effective sans être explicitement demandée, notamment pour certains UID partagés.

Une application non visible est indiquée `NOT_VISIBLE_OR_NOT_INSTALLED` : le diagnostic ne conclut pas automatiquement qu’elle est absente. Aucun inventaire général des applications, position, compte, numéro de série ou clé privée n’est collecté. Les données restent dans le fichier privé de l’application ; le bouton Copier permet au propriétaire de transmettre le rapport. Les seules nouvelles déclarations du manifeste sont des requêtes de visibilité ciblées. Aucune permission système, connexion Internet, commande shell, interface privée constructeur ou modification de réglage n’est ajoutée.

**Un certificat identique à la référence n’est pas une validation d’hébergement.** Sur l’émulateur AOSP Android 13 testé, ce certificat correspond effectivement à la plateforme, tandis que le diagnostic et AutoDrive gardent leurs permissions privilégiées refusées. Avec Maps installé, `eligible` reste faux et `nativeHostingVerified` reste faux. Ni un certificat public, ni un résultat positif de comparaison ne fournissent une clé de signature ou une autorisation constructeur. Le firmware exact de l’Incarlink et une interface d’hébergement prise en charge restent à vérifier.

Résultats locaux 0.3 : compilation debug et tests instrumentés compilés ; lint **0 erreur, 29 avertissements, 1 hint** ; **1 test instrumenté réussi** sur Android 13 ; mise à jour réelle 0.2 → 0.3 réussie ; Maps absent puis Maps 11.129.0103 présent ; actualisation et copie horodatée vérifiées ; écran 800 × 480 vérifié visuellement. AutoDrive est resté en version code 18. Un dialogue de plantage du service système AOSP de vérification des liens a d’abord interrompu l’automatisation UI ; après fermeture de ce dialogue, les contrôles UI ont passé. Ce n’était pas un plantage du diagnostic. Aucun essai sur le téléphone ou le boîtier du propriétaire n’a été effectué.

Preuves réelles : [rapport Maps absent](evidence/firmware-emulator-api33-v03-missing.json), [rapport Maps présent](evidence/firmware-emulator-api33-v03-maps.json), [capture 800 × 480](evidence/firmware-800x480-v03.png). L’inspection `aapt` de l’APK final ne relève que la permission interne AndroidX `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, sans permission système, localisation ou réseau. Signature APK vérifiée : même certificat que 0.2 et AutoDrive. SHA-256 APK 0.3 : `c5859dee704eb0ba1603b2d18ca6341d06e8c9e567662ec1ee78c1df19ffc410`.

Ce diagnostic ne rend pas Maps intégré fonctionnel. [Audit des pistes AB / DUO et limites](../docs/NAVIGATION-AUDIT.md#additional-native-hosting-research--6-november-2026).

## Version 0.2 : identifier l’appareil et dater le contrôle

Les rapports 0.1 ne contenaient pas le modèle de l’appareil. La 0.2 ajoute `probeVersion`, `deviceManufacturer`, `deviceModel`, `deviceBrand`, `buildDisplay`, `androidRelease` et `generatedAtUtc`. La copie ajoute `copiedAtUtc` sans effacer le résultat du contrôle. Le modèle et la version Android apparaissent aussi à l’écran. Aucune position, numéro de série, compte ou connexion réseau n’est lu.

Sur émulateur Android 13 : installation de 0.1 puis mise à jour en 0.2 réussies ; modèle/version renseignés ; nouveau contrôle produisant un nouvel horodatage ; bouton Copier ajoutant l’heure de copie. L’application principale est restée en version code 18. [Rapport réel 0.2](evidence/provenance-emulator-api33-v02.json). Cette version améliore la provenance du diagnostic ; elle ne change pas les autorisations ou la méthode d’intégration native.

Dans AutoDrive 1.5.1, le panneau YouTube est lui aussi une page web (`m.youtube.com`). La sélection d’une application YouTube installée sert à une ouverture externe explicite. Aucune activité YouTube Android n’est actuellement hébergée dans ce panneau ; elle ne fournit donc pas une technique d’intégration native réutilisable pour Maps.

## Utilisation

1. Installer l’APK de test séparément d’AutoDrive, puis ouvrir **AutoDrive Test Maps**.
2. Appuyer sur **Vérifier la compatibilité**.
3. **Tester Maps dans le panneau** reste désactivé si Android n’annonce pas le support ou si Maps n’autorise pas ce type d’hébergement. Il n’ouvre alors aucune autre fenêtre en remplacement.
4. Si les préconditions sont annoncées, le bouton demande un essai natif dans une seule tâche hôte. Le résultat doit encore être vérifié visuellement : un signal Android ne prouve pas une navigation utilisable.
5. **Copier le rapport** permet de partager les informations de compatibilité. Le rapport contient modèle/version de l’appareil, dates de contrôle et copie, version de Maps, composant de lancement, support Android et autorisations d’hébergement ; aucun compte, position GPS ou donnée de navigation n’est lu.

Le contrôle porte sur l’activité de lancement Maps et ses préconditions publiques. Il ne teste pas une interface privée Inlink. Le boîtier du propriétaire n’a pas été testé ni modifié.

## Résultat de l’essai natif 0.1

Test du 5 novembre 2026 sur l’émulateur Android 13, 800 × 480, avec Maps 11.129.0103 :

- AndroidX signale `SplitSupportStatus: AVAILABLE`.
- Maps n’autorise pas l’hébergement non approuvé et ne déclare aucun certificat hôte accepté pour son activité de lancement. Son mode de lancement est `singleTask`.
- Une tentative native a néanmoins été exécutée **sur cet émulateur seulement** pour vérifier le comportement : Android a signalé **zéro panneau intégré**. Maps a pris une autre tâche plein écran et l’hôte est devenu invisible.
- Le manifeste Maps contient aussi une `GhostActivity` Android Auto, mais elle exige `com.google.android.projection.gearhead.permission.START_PROJECTED_ACTIVITY`. Le diagnostic n’a pas cette permission et ne tente pas de la contourner. Cette activité n’est pas un widget public pour AutoDrive.
- **Conclusion limitée à la configuration testée : intégration native non obtenue.** Ce résultat ne démontre pas le comportement de toutes les versions de Maps ou du firmware Incarlink. L’APK permet de relever les préconditions sur cet autre appareil.

Preuves issues de l’APK final : [rapport de compatibilité](evidence/compatibility-emulator-api33.json), [résultat de la tentative native](evidence/native-attempt-emulator-api33.json), [capture de diagnostic](evidence/compatibility-800x480.png). Les API ne rapportent aucun certificat hôte accepté pour l’activité Maps testée. L’inspection de son manifeste n’a trouvé aucune déclaration `allowUntrustedActivityEmbedding` ou `knownActivityEmbeddingCerts`. Le seul `allowEmbedded` historique trouvé concerne la `GhostActivity` protégée d’Android Auto.

Le paramètre de test `emulatorAttempt` n’est accepté que lorsque `Build.HARDWARE` annonce `ranchu` ou `goldfish`. Il sert à reproduire la voie négative avec un lancement Android normal. Il n’accorde aucune permission et n’utilise aucun contournement.

## Compilation et contrôles

```sh
./gradlew :native-maps-test:assembleDebug :native-maps-test:lintDebug
./gradlew :native-maps-test:assembleDebugAndroidTest
# Sur un émulateur de test explicitement sélectionné :
ANDROID_SERIAL=emulator-5568 ./gradlew :native-maps-test:connectedDebugAndroidTest
```

Compilation et lint réussis localement : **0.2 : 0 erreur, 20 avertissements, 1 hint** ; 0.1 : 0 erreur, 18 avertissements, 1 hint. Vérifications de l’essai natif 0.1 : Maps absent ; préconditions bloquées avec Maps installé ; véritable tentative native sans intégration. Vérifications 0.2 : mise à jour, identification de l’appareil, actualisation et copie horodatée du rapport. Les 14 tests JVM de l’application principale avaient également passé le contrôle 0.1. Les installations temporaires ont été retirées de l’émulateur après les essais.

APK Windows 0.2 testé : SHA-256 `62239e918b28d5f9700fcf9fb66b4d007e918fd5bd4eca46094ff4f7d3301b2b`. Ancien APK 0.1 : `8bcc956474d4095431aee8f235bd7f26d5cf144473eee4e0fdadd20279e72cc2`. Leur certificat est identique à celui d’AutoDrive (SHA-256 `1ef249ecb8d4af1bbd730a15aea2248acf5d578dffde2d637fd01b7e5f637efb`), afin que le contrôle des certificats hôtes soit pertinent pour ce lanceur. L’identifiant d’application reste distinct.

L’APK est un **diagnostic expérimental**, pas une nouvelle version fonctionnelle du tableau de bord. La version stable AutoDrive 1.5.1 et son canal de mise à jour restent inchangés. Aucun logiciel n’a été installé sur le boîtier du propriétaire.

Références : [Android — hébergement entre applications](https://developer.android.com/develop/ui/views/layout/activity-embedding#cross-application-embedding), [WindowManager 1.5.1](https://developer.android.com/jetpack/androidx/releases/window), [lancement d’activités sur écrans secondaires](https://source.android.com/docs/core/display/multi_display/activity-launch).
