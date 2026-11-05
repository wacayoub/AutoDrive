# Test séparé de Google Maps Android

Cette application de diagnostic (`com.autodrive.nativeprobe`, Android 13 minimum) essaie l’intégration d’une activité Google Maps avec les API publiques AndroidX WindowManager 1.5.1. Elle ne remplace pas AutoDrive, ne devient pas lanceur HOME et ne charge aucun site web. Aucun accès root, permission privilégiée, API cachée, capture d’écran continue ou injection tactile n’est utilisé.

[Télécharger l’APK expérimental 0.1](https://github.com/wacayoub/AutoDrive/releases/download/native-maps-test-0.1/AutoDrive-Native-Maps-Test-0.1.apk). Cette préversion est séparée de la version stable et ne sera pas proposée comme mise à jour d’AutoDrive.

Dans AutoDrive 1.5.1, le panneau YouTube est lui aussi une page web (`m.youtube.com`). La sélection d’une application YouTube installée sert à une ouverture externe explicite. Aucune activité YouTube Android n’est actuellement hébergée dans ce panneau ; elle ne fournit donc pas une technique d’intégration native réutilisable pour Maps.

## Utilisation

1. Installer l’APK de test séparément d’AutoDrive, puis ouvrir **AutoDrive Test Maps**.
2. Appuyer sur **Vérifier la compatibilité**.
3. **Tester Maps dans le panneau** reste désactivé si Android n’annonce pas le support ou si Maps n’autorise pas ce type d’hébergement. Il n’ouvre alors aucune autre fenêtre en remplacement.
4. Si les préconditions sont annoncées, le bouton demande un essai natif dans une seule tâche hôte. Le résultat doit encore être vérifié visuellement : un signal Android ne prouve pas une navigation utilisable.
5. **Copier le rapport** permet de partager les informations de compatibilité. Le rapport contient version de Maps, composant de lancement, support Android et autorisations d’hébergement ; aucun compte, position GPS ou donnée de navigation n’est lu.

Le contrôle porte sur l’activité de lancement Maps et ses préconditions publiques. Il ne teste pas une interface privée Inlink. Le boîtier du propriétaire n’a pas été testé ni modifié.

## Résultat local réel

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
```

Compilation et lint réussis localement (**0 erreur, 18 avertissements, 1 hint**). Vérifications sur émulateur : Maps absent ; préconditions bloquées avec Maps installé ; véritable tentative native sans intégration. Les 14 tests JVM de l’application principale passent aussi. Les installations temporaires du diagnostic et de Maps ont été retirées de l’émulateur après les essais.

APK Windows testé : SHA-256 `8bcc956474d4095431aee8f235bd7f26d5cf144473eee4e0fdadd20279e72cc2`. Son certificat est identique à celui d’AutoDrive (SHA-256 `1ef249ecb8d4af1bbd730a15aea2248acf5d578dffde2d637fd01b7e5f637efb`), afin que le contrôle des certificats hôtes soit pertinent pour ce lanceur. Son identifiant d’application reste distinct.

L’APK est un **diagnostic expérimental**, pas une nouvelle version fonctionnelle du tableau de bord. La version stable AutoDrive 1.5.1 et son canal de mise à jour restent inchangés. Aucun logiciel n’a été installé sur le boîtier du propriétaire.

Références : [Android — hébergement entre applications](https://developer.android.com/develop/ui/views/layout/activity-embedding#cross-application-embedding), [WindowManager 1.5.1](https://developer.android.com/jetpack/androidx/releases/window), [lancement d’activités sur écrans secondaires](https://source.android.com/docs/core/display/multi_display/activity-launch).
