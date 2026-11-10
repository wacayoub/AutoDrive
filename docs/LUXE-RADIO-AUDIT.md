# Luxe Radio dans Radio Maroc & Coran

Audit du 10 novembre 2026 pour l’écoute du direct de Luxe Radio Maroc depuis Radio Maroc & Coran.

## Distribution et vérification du paquet

Le paquet Android est `app.luxeradio.ma`. Le Play Store de l’émulateur Android 13 affiche **Sign in** en l’absence de session Google. L’archive obtenue séparément pour le test contient la version **3.9.42** (`3090042`). Le certificat SHA-256 du paquet de base et des deux APK fractionnés correspond à une empreinte publiée par Luxe Radio pour ce paquet. [Fiche Play Store](https://play.google.com/store/apps/details?id=app.luxeradio.ma&hl=fr) · [Déclaration de signature du diffuseur](https://open.luxeradio.ma/.well-known/assetlinks.json).

L’image Google Play Android 13 testée expose uniquement l’ABI x86-64. Android a refusé l’installation du jeu ARMv7 avec `INSTALL_FAILED_NO_MATCHING_ABIS`. La tentative avec le paquet de base et le fractionnement de densité a renvoyé `INSTALL_FAILED_MISSING_SPLIT`. [Exigences de compatibilité des ABI Android](https://developer.android.com/google/play/requirements/64-bit).

Le paquet signé a été installé dans un deuxième émulateur, sous Android 11. Son image Google Play x86 prend en charge la traduction des binaires ARMv7. L’installation depuis le Play Store reste à vérifier avec une session Google.

### Installer Luxe Radio sans compte Google

Google Play demande une session Google. L’installation Android du paquet Luxe Radio via ADB et son mode invité ont fonctionné dans l’émulateur sans compte Google. Une fois l’application ouverte, **Get started** donne accès au direct gratuit sans identifiant Luxe Radio. [Comptes Google Play](https://support.google.com/googleplay/answer/2521798?hl=fr) · [Installation de paquets fractionnés avec ADB](https://developer.android.com/tools/adb#move).

APKPure propose Luxe Radio **3.9.42** (`3090042`) en ARM64 et en ARMv7. Les trois APK de chaque archive téléchargée portent le certificat SHA-256 `270e78e9fc529de6ee0c1946b84887278d6e7dad8d35571d42c709bb9bd85b10`, publié par Luxe Radio. Vérifier le certificat avec `apksigner verify --print-certs` avant usage. [Page de téléchargement du miroir tiers](https://apkpure.net/fr/luxe-radio/app.luxeradio.ma/download) · [Certificats publiés par Luxe Radio](https://open.luxeradio.ma/.well-known/assetlinks.json).

- **ARM64**, archive dont l’intégrité, la version et la signature ont été vérifiées. SHA-256 `1f3e47f638c4a0dca0a102ee35d981c68b3c3ccf21b6e54727bd3f001c554ab4`. [Archive ARM64](https://d.apkpure.net/b/XAPK/app.luxeradio.ma?versionCode=3090042&nc=arm64-v8a&sv=24).
- **ARMv7**, archive installée et testée dans l’émulateur. SHA-256 `0754587dfc43010c75684f77d99816fdce8b8009da8146d41f574f8ccb828909`.

Utiliser ADB depuis un ordinateur sur un appareil autorisé pour l’installation. `adb -s NUMERO_DE_SERIE shell getprop ro.product.cpu.abilist` indique les architectures acceptées. Si la liste contient `arm64-v8a`, extraire les trois fichiers de l’archive ARM64 et les installer ensemble depuis le dossier extrait.

```sh
adb -s NUMERO_DE_SERIE install-multiple app.luxeradio.ma.apk config.arm64_v8a.apk config.xxhdpi.apk
```

Pour l’archive ARMv7, la liste doit inclure `armeabi-v7a`.

```sh
adb -s NUMERO_DE_SERIE install-multiple app.luxeradio.ma.apk config.armeabi_v7a.apk config.tvdpi.apk
```

La compatibilité de cette procédure avec l’Incarlink reste à vérifier. Le blocage de la mise à jour Radio Maroc & Coran 2.3.0 relève de Play Protect. En FM, Luxe Radio est disponible sans compte à 99,2 MHz à Casablanca et 105,4 MHz à Rabat. [Fréquences publiées par la HACA](https://www.haca.ma/sites/default/files/upload/Fr%C3%A9quences%20des%20Services%20Radiophoniques%20%C3%A9dit%C3%A9s%20par%20les%20op%C3%A9rateurs%20Priv%C3%A9s_1.pdf).

## Direct et service média

Le contrôle du flux gratuit avec une session invitée du lecteur officiel a renvoyé HTTP 200 et plusieurs trames AAC consécutives. Une requête sans jeton a renvoyé HTTP 401. Le lien public du diffuseur ouvre son application. [Direct officiel](https://open.luxeradio.ma/radio/live).

Le manifeste de la version 3.9.42 déclare `ma.luxeradio.auto.AutoService` comme service exporté pour l’action `android.media.browse.MediaBrowserService`. Le test utilise `MediaBrowser` et `MediaController` depuis une application distincte. Android laisse le fournisseur accepter ou refuser chaque client. [Référence Android MediaBrowserService](https://developer.android.com/reference/android/service/media/MediaBrowserService).

Au premier essai, le service a accepté la connexion de la sonde mais a renvoyé une erreur en parcourant le catalogue. Après ouverture de Luxe Radio et sélection de **Get started**, la sonde a trouvé le dossier `radioTab` puis l’élément `radioTab_live-streaming`. La commande Android `playFromMediaId` a déclenché l’état `STATE_PLAYING` et une piste audio active appartenant à Luxe Radio. La sonde est restée au premier plan. Aucun compte n’a été saisi lors de cet essai. [Commandes de lecture Android](https://developer.android.com/reference/android/media/session/MediaController.TransportControls).

Radio Maroc & Coran vérifie la signature du paquet installé, parcourt le catalogue publié par son service média et commande le direct. Luxe Radio gère le réseau, l’accès au flux et la sortie audio. Le lien officiel sert d’identifiant pour les favoris. L’application propose une action de configuration si le service ne peut pas lire son catalogue. Le dépôt GitHub contient le code de Radio Maroc & Coran et cet audit. Le paquet tiers et les données de session restent hors du dépôt.

## Essais de Radio Maroc & Coran 2.3.0

- **Android 11, Luxe Radio 3.9.42**. Le lecteur est resté au premier plan. La sortie audio active, le badge LIVE, le logo, le titre « Les Matins Luxe », la pause, la reprise et le retour après Home ont été vérifiés à 800 × 480. Le passage à Med Radio a arrêté le son de Luxe Radio.
- **Android 13, Luxe Radio absent**. La station propose d’installer l’application officielle. La boîte de dialogue reste dans Radio Maroc & Coran jusqu’à une action explicite. La mise à jour signée par-dessus la 2.2.0 a conservé le favori de test.
- **Build**. APK signé avec le certificat du canal Radio Maroc, 16 tests unitaires réussis, 0 erreur et 26 avertissements lint.

La disponibilité de Luxe Radio dans le Play Store du boîtier et la lecture sur l’Incarlink restent à vérifier.

## Distribution de la 2.3.0 suspendue

L’application 2.2.0 a détecté la version GitHub, téléchargé l’APK et ouvert l’installateur Android. Sur l’image Android 13 avec Google Play, l’analyse Play Protect a affiché **Harmful app blocked** pour **Radio Maroc & Coran**. Le message détaillé est resté générique. L’essai s’est arrêté à ce blocage. [Guide Google sur ce message](https://developers.google.com/android/play-protect/warning-dev-guidance?hl=fr#harmful-app-blocked).

L’APK téléchargé correspond à l’empreinte publiée. La signature, l’alignement APK et l’intégrité ZIP ont été vérifiés. Les autorisations du manifeste signé sont identiques à celles de la 2.2.0, dont `REQUEST_INSTALL_PACKAGES` pour transmettre une mise à jour à l’installateur système. Ces contrôles ne déterminent pas la raison du classement Play Protect.

La version 2.3.0 a été repassée en brouillon sur GitHub. Le manifeste de mise à jour de cette version n’est plus accessible publiquement. Le code de l’intégration et cet audit restent consultables. [Procédure officielle de réexamen](https://support.google.com/android/answer/2992033?hl=fr).
