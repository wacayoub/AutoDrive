# Radio Maroc & Coran

Application Android 11+ pour les téléphones, les boîtiers Android tels que l’Incarlink et Android TV. Le même APK signé s’adapte à l’écran. Sur l’Incarlink, l’application se lance depuis la grille Quickstep. Sur Android TV, elle apparaît dans la liste des applications avec sa bannière. Une connexion Internet est nécessaire pour écouter. [Lancement des applications sur Android TV](https://developer.android.com/training/tv/get-started/create).

## Démarrage voiture et lecteur Android

Dans **AppBoot** ou le menu d’autodémarrage de l’Incarlink, choisis **Radio Maroc & Coran**. Dans la radio, touche l’icône voiture et choisis **À chaque ouverture**. Sélectionne ensuite la station à mémoriser. Quand le boîtier ouvre la radio ou la ramène au premier plan, elle lance la dernière station sélectionnée et peut attendre le réseau jusqu’à 30 secondes. Ce mode s’applique aussi aux ouvertures manuelles. Le mode **Après redémarrage Android** limite la reprise à la première ouverture après un redémarrage complet. L’ouverture par AppBoot dépend du réglage et du comportement du boîtier. [Restrictions Android sur l’ouverture d’une activité en arrière-plan](https://developer.android.com/guide/components/activities/background-starts).

Le lecteur système Android reçoit le nom de la station, son logo et le titre de l’émission ou du morceau lorsque le diffuseur le fournit. Le logo de l’application sert de secours. Les programmes disponibles de SNRT et Radio 2M se rafraîchissent dans le service audio pendant la lecture. Luxe Radio fournit ses propres informations de lecture via son application officielle. [Métadonnées et commandes de la session média](https://developer.android.com/media/media3/session/background-playback).

La 2.4.0 déclare une source audio Android Auto avec le catalogue embarqué **Radio Maroc** et **القرآن**. Le lecteur de la voiture choisit la source et les champs qu’il affiche. Le lancement AppBoot, l’affichage sur l’Incarlink et la projection dans un véhicule restent à vérifier sur le matériel. [Découverte des sources audio Android Auto](https://developer.android.com/training/cars/media/auto).

Les commandes média Android **précédente** et **suivante** changent la station dans la liste filtrée de la radio, même en arrière-plan. Depuis la 2.4.2, elles lancent la station choisie même si la précédente était en pause. Les boutons **TRACK / SEEK** de la Toyota dépendent de la transmission de ces commandes par l’Incarlink. Luxe Radio utilise la session média de son application officielle. [Commandes média Android](https://developer.android.com/media/media3/session/control-playback).

## Mises à jour en ligne

À l’ouverture, la radio vérifie si une nouvelle version compatible est publiée sur GitHub. La flèche **Mises à jour** en haut permet aussi de lancer la recherche. **OK** télécharge et vérifie l’APK, puis ouvre l’installateur. Au premier passage, Android peut demander d’autoriser **Radio Maroc & Coran** comme source. Revenir ensuite à la radio pour poursuivre et confirmer **Mettre à jour** dans Android. Les favoris et réglages sont conservés. [Autorisation d’installation Android](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()).

La vérification automatique s’effectue à l’ouverture au plus une fois par jour après une recherche réussie. Elle fonctionne au premier plan. Un téléchargement déjà autorisé attend le retour dans la radio avant d’ouvrir l’installateur. **Reprendre l’installation** permet de rouvrir l’installateur pour un fichier déjà vérifié.

Pour publier une mise à jour, augmenter le code et le nom de version, signer l’APK avec le certificat Radio Maroc habituel, puis publier avec la commande ci-dessous depuis la racine du dépôt. Le manifeste `radio-maroc-update.json` est généré à partir de l’APK. Le canal `radio-maroc-vX.Y.Z` est distinct de la dernière version stable du lanceur AutoDrive. [Canal GitHub Releases](https://github.com/wacayoub/AutoDrive/releases).

```sh
python tools/github_release.py --radio-maroc --apk radio-maroc/build/outputs/apk/release/radio-maroc-release.apk --output build/radio-publish
```

## Écoute

Dans **Affichage → Jour / Nuit** (**Affichage TV** sur Android TV), choisis **Jour** pour le thème blanc et bleu cobalt, **Nuit** pour le thème sombre ou **Système** pour suivre le réglage d’Android. La radio mémorise ce choix pour les trois vues.

Le thème graphite et turquoise regroupe les radios, le Coran et les favoris dans une barre de catégories avec icônes. Sur l’Incarlink, le lecteur occupe la colonne de gauche et les catégories commencent au bord de la liste. En portrait, les commandes restent en bas de l’écran.

Ouvre **Affichage** sur le téléphone ou le boîtier, ou **Affichage TV** sur Android TV. **Guide** présente les stations en liste, **Mosaïque** les affiche en grille et **Écran d’écoute** agrandit le lecteur. Le choix est mémorisé. **Retour** ou **Toutes les radios** depuis l’écran d’écoute ramène au dernier catalogue. Sur Android TV, les flèches déplacent le cadre de sélection et **OK** lance la station. L’étoile du lecteur gère les favoris. [Navigation à la télécommande](https://developer.android.com/training/tv/get-started/controllers).

- **Radio Maroc** réunit les radios SNRT, les autres stations de la sélection et les flux HTTPS marocains de Radio Browser. **Trier** propose Popularité, Nom A–Z, Nom Z–A et Par groupe. Le tri est mémorisé par onglet. Le mode Popularité utilise les données d’audience et les clics de l’annuaire pour ordonner les radios. La liste affiche les noms et les genres. [Données d’audience publiées](https://aujourdhui.ma/culture/elle-a-accumule-une-part-daudience-de-1536-la-radio-mohammed-vi-du-saint-coran-en-tete-du-podium) · [Documentation de Radio Browser](https://de1.api.radio-browser.info/).
- Les radios régionales sont regroupées en bas de **Radio Maroc**, quel que soit le tri. Les onze antennes de la SNRT rejoignent Cap Radio, Radio Tanger Med, Marrakech Plus, Radio Plus Agadir et Skyrock Casablanca. Les directs de la SNRT utilisent les flux audio de son lecteur régional. La recherche accepte le nom de la station ou de la région. [Lecteur régional SNRT Live](https://snrtlive.ma/fr/radio-regionale) · [Radios régionales de la SNRT](https://snrtnews.com/article/la-snrt-met-la-proximite-le-debat-et-lancrage-regional-au-coeur-de-sa-nouvelle-saison).
- Les sept webradios HIT RADIO et les quatorze flux Medi 1 sont inclus dans Radio Maroc. Le tri **Par groupe** réunit HIT RADIO, Medi 1 et la SNRT. On peut aussi rechercher les noms des groupes. [Webradios HIT RADIO](https://www.hitradio.ma/web-radio) · [Webradios Medi 1](https://www.medi1.com/fr/webradios).
- Recherche **Luxe Radio** dans l’onglet **Radio Maroc**. Installe l’application officielle Luxe Radio et ouvre-la une fois pour terminer sa configuration. Reviens ensuite dans Radio Maroc & Coran et sélectionne la station pour écouter le direct. Le service média de Luxe Radio gère la lecture pendant que Radio Maroc & Coran reste à l’écran. [Luxe Radio sur Google Play](https://play.google.com/store/apps/details?id=app.luxeradio.ma&hl=fr) · [Audit de l’intégration](../docs/LUXE-RADIO-AUDIT.md).
- Radio Nationale, Radio Amazighe, Chaîne Inter et Mohammed VI du Saint Coran sont accessibles dans la liste Radio Maroc ou en recherchant « SNRT ». Le lecteur intégré demande les jetons temporaires fournis par le service EasyBroadcast utilisé par SNRT Live. En cas de refus du serveur, la station reste sélectionnée et la commande de lecture permet de réessayer. [SNRT Live](https://snrtlive.ma/) · [Radio 2M](https://radio2m.ma/).
- **Monde** affiche le continent du pays actif. Appuie sur le pays pour ouvrir la grille des continents, puis sélectionne ou recherche un pays dans la région choisie. Le Maroc a son propre onglet. **Charger plus de radios** charge la page suivante. La première page consultée de chaque pays est gardée localement pour consulter la liste hors connexion. Les favoris des stations de l’annuaire restent disponibles en changeant de pays. La couverture dépend des stations répertoriées et disposant d’un flux HTTPS compatible. [Classification M49 de l’ONU](https://unstats.un.org/unsd/methodology/m49/overview/) · [Documentation de Radio Browser](https://api.radio-browser.info/).
- **القرآن** réunit les sections **الترتيل**, **التجويد** et **تلاوات الحرم** dans une même liste. La recherche accepte le nom d’une section ou d’un récitant. **الترتيل** propose 21 flux, dont بدر التركي, العيون الكوشي, عمر القزابري, فارس عباد et ناصر القطامي. Le catalogue comprend aussi محمود خليل الحصري en lecture Warsh. [Flux publiés par MP3Quran](https://www.mp3quran.net/api/v3/radios?language=ar).
- **التجويد** regroupe quatre flux du **المصحف المجوّد**. On y retrouve عبد الباسط عبد الصمد, محمد صديق المنشاوي, محمود خليل الحصري et محمود علي البنا. Le flux مجوّد d’Al-Minshawi garde son adresse de lecture pour préserver les favoris enregistrés.
- **تلاوات الحرم** rassemble six extraits coraniques du Fajr, du Maghrib et de l’Isha à la Mosquée sacrée de La Mecque. Chaque extrait commence après la Fatiha et s’arrête avant le passage au rukūʿ. L’entrée indique le récitant, la sourate, la prière et la date. Le lecteur utilise les liens publics de Haramain Salaah Recordings, hébergés par QuranicAudio pour l’écoute personnelle. [Archives Haramain](https://haramain.info/) · [Conditions d’utilisation de QuranicAudio](https://quranicaudio.com/about).
- Les étoiles enregistrent les favoris. Le bouton **★ Favoris** à côté de la recherche dans Radio Maroc affiche les radios marocaines étoilées dans le même ordre de popularité. Les commandes précédente/suivante suivent la liste affichée. L’onglet **★ Favoris** rassemble les favoris de toutes les catégories.
- Les titres arabes et leurs portraits se lisent de droite à gauche dans la liste. La station sélectionnée garde un fond coloré dans le Guide et la Mosaïque, même en pause.
- La dernière station est mémorisée pour une reprise manuelle au lancement. Pendant la lecture, l’application signale une coupure quand le flux ne joue plus et retente automatiquement au retour du réseau tant que la session reste active. Une pause manuelle annule cette reprise. Les commandes média Android restent accessibles en arrière-plan.
- Le badge rouge **LIVE** et ses deux traits s’affichent pendant la lecture d’un direct. Le lecteur peut afficher un titre fourni par le flux ou le programme publié par SNRT Live et Radio 2M. Il affiche l’horaire si le diffuseur le publie et sépare cette information de l’état audio. [SNRT Live](https://snrtlive.ma/) · [Données officielles de Radio 2M](https://bo.radio2m.ma/api/live).

Le catalogue est embarqué. Selon la station, le lecteur ouvre le flux de l’hébergeur ou commande le service média de Luxe Radio. Les lecteurs officiels de certains diffuseurs publient les adresses de leurs flux. Les autres entrées de la sélection marocaine proviennent de Radio Browser. Les stations et horaires peuvent évoluer. [Radio Browser](https://www.radio-browser.info/) · [HIT RADIO](https://www.hitradio.ma/live) · [Radio Mars](https://radiomars.ma/radio-live/) · [Chada FM](https://chada.ma/test-live/) · [Radio Aswat](https://radioaswat.ma/).

Les picons de la sélection initiale sont chargés depuis les sites des diffuseurs ou la HACA. Skyrock Casablanca utilise le logo du réseau Skyrock. Les autres stations utilisent le logo fourni par l’annuaire lorsqu’il est accessible. Le lecteur garde les images en cache et affiche des initiales lorsque l’image est indisponible. [Med Radio](https://medradio.ma/) · [HIT RADIO](https://www.hitradio.ma/) · [Radio Aswat](https://radioaswat.ma/) · [Chada FM](https://chada.ma/fr/) · [Medina FM](https://medinafm.ma/) · [U Radio](https://www.uradio.ma/) · [Skyrock](https://www.skyrock.fm/) · [HACA](https://www.haca.ma/fr/radio).

## Archives de La Mecque

| Prière | Récitant | Sourate | Date | Notice d’origine |
| --- | --- | --- | --- | --- |
| الفجر | ماهر المعيقلي | الرحمن | 7 nov. 2026 | [Haramain](http://www.haramain.info/2026/11/makkah-fajr-07th-november-2026.html) |
| الفجر | عبد الله الجهني | الأنعام | 8 nov. 2026 | [Haramain](http://www.haramain.info/2026/11/makkah-fajr-08th-november-2026.html) |
| المغرب | بدر التركي | التين | 8 nov. 2026 | [Haramain](http://www.haramain.info/2026/11/makkah-maghrib-08th-november-2026.html) |
| المغرب | ماهر المعيقلي | الأحزاب | 6 nov. 2026 | [Haramain](http://www.haramain.info/2026/11/makkah-maghrib-06th-november-2026.html) |
| العشاء | ياسر الدوسري | الفرقان | 8 nov. 2026 | [Haramain](http://www.haramain.info/2026/11/makkah-isha-08th-november-2026.html) |
| العشاء | بدر التركي | التوبة | 26 oct. 2026 | [Haramain](http://www.haramain.info/2026/10/makkah-isha-26th-october-2026.html) |

Les limites de lecture ont été relevées dans l’audio source à l’aide de la transcription et de la forme d’onde. Les adresses des six fichiers servent toujours d’identifiant pour retrouver les favoris des anciennes versions.

## Crédits photo

Le portrait du lecteur ouvre les crédits. Les images proviennent de Wikimedia Commons. Trois fichiers sont les miniatures Commons de 250 px. Les autres fichiers sont repris tels quels depuis leur notice. Une icône de livre accompagne les récitateurs sans portrait disponible sous licence de réutilisation.

| Récitant | Crédit et licence | Source |
| --- | --- | --- |
| بدر التركي | Abo.ibrahim0, CC0 1.0 | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Photo_of_badr_al-turki_in_2023.jpg) |
| العيون الكوشي | Hamdaouiabde, CC BY-SA 4.0 | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:%D8%B5%D9%88%D8%B1%D8%A9_%D9%84%D9%84%D9%82%D8%A7%D8%B1%D8%A6_%D8%A7%D9%84%D8%B4%D9%8A%D8%AE_%D8%A7%D9%84%D8%B9%D9%8A%D9%88%D9%86_%D8%A7%D9%84%D9%83%D9%88%D8%B4%D9%8A.jpeg) |
| عبد الباسط عبد الصمد | Auteur inconnu, photo de 1956, domaine public selon Commons | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Abdul_Basit_Abd_us-Samad.jpg) |
| محمد صديق المنشاوي | Radio du Coran égyptienne, auteur inconnu, domaine public selon Commons | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Minshawy.jpg) |
| محمود خليل الحصري | Zubairkhan1, domaine public | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Hussary.jpg) |
| ماهر المعيقلي | Sazwanmisuari, CC0 1.0 | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Mahir_Al-Muaiqly.png) |
| سعود الشريم | Sazwanmisuari, CC0 1.0 | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Saud_Shuraim.png) |
| أحمد العجمي | أحمد زين, CC BY-SA 3.0 | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Ahmad_bin_Ali_Al-Ajmi.png) |
| ياسر الدوسري | أحمد زين, CC BY-SA 3.0 | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Yasser_Al-Dosari.png) |
| عبد الله الجهني | الشيخ هيثم الدخين, CC BY-SA 4.0 | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Abdullah_Al_Juhany_%28Cropped%29.png) |

## Vérification des sources de Radio 2M et de la SNRT

Le flux de Radio 2M correspond au champ audio `link_ecouter` du service utilisé par son site, sur le CDN de son lecteur. Le manifeste, sa variante et un segment audio ont répondu. Le segment contrôlé contenait de l’AAC décodable. Le titre d’émission était absent de la réponse contrôlée. [Lecteur Radio 2M](https://radio2m.ma/) · [Données officielles du direct](https://bo.radio2m.ma/api/live).

Les quatre pages de SNRT Live publient des flux HLS EasyBroadcast distincts. Une requête directe sans jeton reçoit HTTP 403. Avec un jeton temporaire obtenu auprès du service du lecteur officiel, les quatre manifestes, leurs variantes et des segments audio ont répondu lors des essais. L’application garde le jeton en mémoire jusqu’au renouvellement et ne transmet les paramètres qu’aux listes HLS correspondantes. [Radio Nationale](https://snrtlive.ma/fr/alidaa-alwatania) · [Radio Amazighe](https://snrtlive.ma/fr/alidaa-alamazighia) · [Chaîne Inter](https://snrtlive.ma/fr/chaine-inter) · [Mohammed VI du Saint Coran](https://snrtlive.ma/fr/idaat-mohammed-assadiss).

## Vérifications de la version 1.8.1

Sur l’émulateur Android 13 en 800 × 480, Radio 2M a atteint l’état « En direct » et la pause a fonctionné. Les quatre stations SNRT sont restées dans le lecteur et ont affiché « Source inaccessible · réessayer ». Med Radio a joué après ces essais. La mise à jour signée depuis 1.8.0 a conservé le favori Chaîne Inter. Les cinq URL de logos officielles ont renvoyé des images PNG. Les dix tests unitaires ont réussi et lint a signalé 0 erreur et 7 avertissements. Le test sur l’Incarlink reste à faire.

## Vérifications de la version 1.8.0

Sur l’émulateur Android 13, la mise à jour signée depuis 1.7.1 a conservé le favori Med Radio. Le classement et les parts d’audience affichées ont été vérifiés, ainsi que le démarrage de Med Radio en direct. La grille des continents, le retour à la grille, la recherche d’un pays, le chargement des stations de Tunisie et du Canada et la mémorisation du pays après redémarrage ont été contrôlés. La grille a été vérifiée en 800 × 480 à 160 et 213 dpi, ainsi qu’en portrait. Les dix tests unitaires ont réussi. Lint a relevé 0 erreur et 7 avertissements. Le test sur l’Incarlink reste à faire.

## Vérifications de la version 1.7.1

Sur l’émulateur Android 13 à 800 × 480, les radios SNRT étaient accessibles dans la liste Radio Maroc et via la recherche. La mise à jour signée depuis 1.7.0 a conservé le favori Chaîne Inter. Son lecteur officiel s’est ouvert dans le navigateur. Huit tests unitaires ont réussi et lint a relevé 0 erreur et 7 avertissements.

## Vérifications de la version 1.7.0

Sur l’émulateur Android 13, MFM Radio et France Inter ont joué depuis l’annuaire. La recherche, la pagination pour la France, le changement de pays et la consultation de la première page du Canada hors connexion ont été vérifiés. Les favoris de 1.6.0 et ceux ajoutés depuis l’annuaire sont restés disponibles après la mise à jour ou le redémarrage. Les quatre entrées SNRT étaient visibles, et les pages officielles de Chaîne Inter et Radio 2M se sont ouvertes. Le rendu a été vérifié à 800 × 480 en 160 et 213 dpi. Huit tests unitaires ont réussi, lint a relevé 0 erreur et 7 avertissements, et la compilation Kotlin du lanceur AutoDrive a réussi. Les adresses audio directes trouvées sur les sites de la SNRT et de Radio 2M ont renvoyé des erreurs depuis l’environnement de test. Le son dans ces lecteurs externes et le comportement sur l’Incarlink restent à vérifier.

## Vérifications de la version 1.6.0

Les douze picons se sont affichés sur l’émulateur Android 13. Le nouveau lecteur a été vérifié en 800 × 480 à 160 et 213 dpi et en portrait. Les favoris ont été conservés lors de la mise à jour signée de 1.5.0. La navigation entre les favoris, la pause, la lecture d’un extrait et la reprise de HIT RADIO après une coupure ont été testées. Six tests unitaires ont réussi et lint a signalé 0 erreur et 6 avertissements. La compilation Kotlin du lanceur AutoDrive a également réussi. Le test sur l’Incarlink reste à faire.

## Vérifications de la version 1.5.0

L’installation signée de 1.4.2 vers 1.5.0 conserve les favoris. L’émulateur Android 13 a confirmé le filtre Radio Maroc et les commandes précédente/suivante dans la liste filtrée. Le rendu a été inspecté à 800 × 480 en 160 et 213 dpi, ainsi qu’en portrait. Après désactivation du Wi-Fi et des données mobiles, HIT RADIO a affiché le message de coupure et a repris après le retour du réseau, alors que l’application était en arrière-plan. Une pause volontaire pendant la coupure a empêché la reprise automatique. Six tests unitaires ont réussi. Lint a signalé 0 erreur et 6 avertissements. La reprise reste à vérifier sur l’Incarlink.

## Vérifications de la version 1.4.0

Sur l’émulateur Android 13, les six archives ont atteint l’état de lecture. Les commandes précédente et suivante, la reprise à la position de pause et le dialogue de crédit du portrait ont été vérifiés. La mise à jour signée de 1.3.0 a conservé le favori et l’onglet de test. Le rendu a été contrôlé en 800 × 480 à 160 et 213 dpi ainsi qu’en portrait. Cinq tests unitaires ont réussi. Lint a signalé 0 erreur et 7 avertissements. Le test sur l’Incarlink reste à faire.

## Vérifications de la version 1.3.0

Les cinq flux MP3Quran de cette mise à jour ont atteint l’état de lecture sur l’émulateur Android 13. Les boutons précédente et suivante, la pause et la disposition des quatre onglets ont été vérifiés en 800 × 480 à 160 et 213 dpi ainsi qu’en portrait. L’installation signée par-dessus 1.2.0 a conservé le favori مجوّد d’Al-Minshawi et l’onglet de test. Trois tests unitaires ont réussi et lint a signalé 0 erreur et 7 avertissements.

## Vérifications de la version 1.2.0

L’installation signée par-dessus 1.1.0 a conservé le favori et l’onglet de test. Sur l’émulateur Android 13, HIT RADIO, Radio Mars et العيون الكوشي ont atteint l’état de lecture. Les commandes précédente/suivante, la pause et la lecture après Home ont répondu. Le rendu a été inspecté en 800 × 480 à 160 et 213 dpi, ainsi qu’en portrait. Deux tests unitaires ont réussi et lint a signalé 0 erreur et 7 avertissements.

## Vérifications de la version 1.1.0

La mise à jour signée de 1.0.0 vers 1.1.0 a conservé le favori et l’onglet de test. Sur l’émulateur Android 13 en 800 × 480, les essais ont confirmé la lecture de HIT RADIO, Radio Mars et العيون الكوشي, les commandes précédente/suivante, la pause et la lecture après Home. Le rendu a été contrôlé à 160 et 213 dpi. Les deux tests unitaires Radio ont réussi et lint a signalé 0 erreur et 6 avertissements.

## Vérifications de la version 1.0.0

- Les 24 flux sélectionnés ont fourni des octets audio lors de vérifications HTTPS. Les essais ont aussi écarté trois entrées MP3Quran qui renvoyaient systématiquement une erreur pendant le contrôle.
- Sur l’émulateur Android 13, HIT RADIO, Radio Mars, العيون الكوشي et عمر القزابري ont atteint l’état de lecture. La navigation entre stations, la pause, la reprise, la lecture après Home, les favoris, la relance et la reprise après une coupure réseau ont été vérifiés sur l’APK signé.
- L’interface a été inspectée en paysage 800 × 480 à 160 et 213 dpi et en portrait. Deux tests unitaires Radio et 39 tests unitaires AutoDrive ont réussi. Lint Radio a signalé 0 erreur et 6 avertissements. La sortie audio sur le boîtier du propriétaire reste à vérifier.
- SHA-256 de `Radio-Maroc-Coran-1.0.0.apk` **`4466cbf8db105c2913e5f14dfd86c4be7969ea61e15ea7d6531fa3b52545e790`**.

## Compilation

Utiliser le JDK 17 et le SDK Android 35, comme pour le projet principal.

```sh
./gradlew :radio-maroc:assembleDebug :radio-maroc:testDebugUnitTest :radio-maroc:lintDebug
```

Pour `:radio-maroc:assembleRelease`, configurer `RADIO_MAROC_KEYSTORE_FILE` et `RADIO_MAROC_KEYSTORE_PASSWORD` avec un keystore privé contenant l’alias `radio-maroc`. Une mise à jour de `com.autodrive.radiomaroc` doit garder le certificat d’origine et augmenter `versionCode`.
