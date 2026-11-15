# Radio Maroc & Coran

Application Android 11+ pour les téléphones, les boîtiers Android tels que l’Incarlink et Android TV. Le même APK signé s’adapte à l’écran. Sur l’Incarlink, l’application se lance depuis la grille Quickstep. Sur Android TV, elle apparaît dans la liste des applications avec sa bannière. Une connexion Internet est nécessaire pour écouter. [Lancement des applications sur Android TV](https://developer.android.com/training/tv/get-started/create).

## Démarrage voiture et lecteur Android

Dans **AppBoot** ou le menu d’autodémarrage de l’Incarlink, choisis **Radio Maroc & Coran**. Dans la radio, ouvre **Menu ⋮ → Démarrage voiture** et choisis **À chaque ouverture**. Sélectionne ensuite la station à mémoriser. Quand le boîtier ouvre la radio ou la ramène au premier plan, elle lance la dernière station sélectionnée et peut attendre le réseau jusqu’à 30 secondes. Ce mode s’applique aussi aux ouvertures manuelles. Le mode **Après redémarrage Android** limite la reprise à la première ouverture après un redémarrage complet. L’ouverture par AppBoot dépend du réglage et du comportement du boîtier. [Restrictions Android sur l’ouverture d’une activité en arrière-plan](https://developer.android.com/guide/components/activities/background-starts).

Le lecteur système Android et les lecteurs compatibles avec la session média reçoivent le nom de la station, son logo et le titre de l’émission ou du morceau lorsque le diffuseur les fournit. Les picons du catalogue embarqué sont aussi accessibles pendant la navigation dans Android Auto. Le logo de l’application sert de secours. Les programmes disponibles de SNRT et Radio 2M se rafraîchissent dans le service audio pendant la lecture. Luxe Radio fournit ses propres informations de lecture via son application officielle. [Illustrations dans Android Auto](https://developer.android.com/training/cars/media/create-media-browser/media-artwork).

Les commandes média Android **précédente** et **suivante** changent la station dans la liste filtrée de la radio, même en arrière-plan. Depuis la 2.4.2, elles lancent la station choisie même si la précédente était en pause. Les boutons **TRACK / SEEK** de la Toyota dépendent de la transmission de ces commandes par l’Incarlink. Luxe Radio utilise la session média de son application officielle. [Commandes média Android](https://developer.android.com/media/media3/session/control-playback).

Lorsque tu changes de station pendant qu’une autre application est ouverte, Android peut afficher son nom en haut de l’écran pendant environ 3,5 secondes. **Menu ⋮ → Pop-up des stations** ouvre les autorisations ou les réglages du canal **Changements de station**. [Notifications flottantes Android](https://developer.android.com/develop/ui/views/notifications#Heads-up).

## Android Auto

Installe ou mets à jour Radio Maroc & Coran sur le téléphone qui projette Android Auto. Une fois le téléphone connecté, ouvre Radio Maroc & Coran depuis le menu des applications de la voiture.

La version 2.5.32 fournit trois onglets à l’interface média d’Android Auto. **Radio Maroc** regroupe les nationales et webradios, SNRT, les stations régionales et la musique arabe. **القرآن** donne accès au tartil, au tajwid et aux récitations du Haram. **Favoris** reprend les stations compatibles enregistrées sur le téléphone. La recherche accepte les noms et les alias du catalogue. Précédent et suivant suivent la catégorie depuis laquelle la lecture a été lancée. Les flux qui nécessitent une application tierce, comme Luxe Radio, sont accessibles depuis leur source dédiée lorsqu’elle est proposée par la voiture.

Android Auto dessine les onglets et le lecteur à partir des catégories, des commandes et des métadonnées envoyées par l’application. Un client média externe sur l’émulateur Android 13 a vérifié la navigation, la recherche, l’accès aux favoris, les illustrations locales et la lecture avec les changements de station. Le rendu projeté et les commandes physiques restent à vérifier sur la Toyota. [Intégration média Android Auto](https://developer.android.com/media/implement/surfaces/cars).

## Mises à jour en ligne

À l’ouverture, la radio vérifie si une nouvelle version compatible est publiée sur GitHub. **Menu ⋮ → Mises à jour** permet aussi de lancer la recherche. **OK** télécharge et vérifie l’APK, puis ouvre l’installateur. Au premier passage, Android peut demander d’autoriser **Radio Maroc & Coran** comme source. Revenir ensuite à la radio pour poursuivre et confirmer **Mettre à jour** dans Android. Les favoris et réglages sont conservés. [Autorisation d’installation Android](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()).

La vérification automatique s’effectue à l’ouverture au plus une fois par jour après une recherche réussie. Elle fonctionne au premier plan. Un téléchargement déjà autorisé attend le retour dans la radio avant d’ouvrir l’installateur. **Reprendre l’installation** permet de rouvrir l’installateur pour un fichier déjà vérifié.

Pour publier une mise à jour, augmenter le code et le nom de version, signer l’APK avec le certificat Radio Maroc habituel, puis publier avec la commande ci-dessous depuis la racine du dépôt. Le manifeste `radio-maroc-update.json` est généré à partir de l’APK. Le canal `radio-maroc-vX.Y.Z` est distinct de la dernière version stable du lanceur AutoDrive. [Canal GitHub Releases](https://github.com/wacayoub/AutoDrive/releases).

```sh
python tools/github_release.py --radio-maroc --apk radio-maroc/build/outputs/apk/release/radio-maroc-release.apk --output build/radio-publish
```

## Écoute

Dans **Menu ⋮ → Jour / Nuit**, choisis **Jour** pour le thème ivoire, noir encre et vert émeraude, **Nuit** pour le thème sombre ou **Système** pour suivre le mode sombre d’Android, y compris son horaire programmé. La radio mémorise ce choix pour les trois vues.

Dans **Menu ⋮ → Effets sonores**, **Clics de l’interface** active le son intégré des boutons et des changements de station. Son niveau suit le volume média. Les profils **Normal**, **Basses** et **Voix** traitent le son décodé du lecteur intégré pendant l’écoute. Les barres autour du logo suivent le niveau des basses, des médiums et des aigus. Luxe Radio gère le son dans sa propre application. [Traitement PCM Media3](https://developer.android.com/reference/androidx/media3/exoplayer/audio/DefaultAudioSink.Builder#setAudioProcessors(androidx.media3.common.audio.AudioProcessor%5B%5D)).

**Dolby** reste visible en gris quand l’autorisation est désactivée ou quand aucun panneau compatible n’est détecté. **Dolby · ON** prend la couleur d’accent quand la radio est autorisée à utiliser la session Dolby. **Réglages Dolby** ouvre le panneau du fabricant, qui applique l’effet selon ses propres réglages. L’autorisation est désactivée par défaut. [Panneau d’effets audio Android](https://developer.android.com/reference/android/media/audiofx/AudioEffect#ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).

Le thème graphite et turquoise regroupe les radios, le Coran et les favoris dans une barre de catégories avec icônes. Sur l’Incarlink, le lecteur occupe la colonne de gauche et les catégories commencent au bord de la liste. En portrait, les commandes restent en bas de l’écran.

Ouvre **Menu ⋮ → Affichage**. **Guide** présente les stations en liste, **Mosaïque** les affiche en grille et **Écran d’écoute** agrandit le lecteur. Le choix est mémorisé. **Retour** ou **Toutes les radios** depuis l’écran d’écoute ramène au dernier catalogue. Sur Android TV, les flèches déplacent le cadre de sélection et **OK** lance la station. L’étoile du lecteur gère les favoris. [Navigation à la télécommande](https://developer.android.com/training/tv/get-started/controllers).

L’interface s’adapte à la résolution de rendu fournie par Android, jusqu’à 3840 × 2160 sur un appareil compatible. Les logos sont décodés à une taille adaptée à leur affichage, dans la limite du fichier d’origine. Les petits écrans utilisent des images plus légères. [Résolutions et densités sur Android TV](https://developer.android.com/training/tv/playback/compose/layouts#manage-layout-resources-for-tv).

Sur Smart TV, **Guide TV** affiche un catalogue compact sur deux colonnes, ou trois quand la largeur disponible le permet. Le nom et le picon suffisent pour parcourir les stations à la télécommande. Le lecteur à droite affiche les informations de la station sélectionnée.

En paysage sur l’Incarlink, **Menu ⋮ → Affichage → Guide** reprend le lecteur à droite, les picons dans le catalogue à gauche, l’horloge, les cartes Météo et Salat et le bandeau Infos. Les titres changent automatiquement et restent fixes pendant leur affichage sur l’écran tactile. Lorsque l’espace vertical est réduit, Météo et Salat sont accessibles depuis **Infos**.

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

Le portrait du lecteur ouvre les crédits. Les images proviennent de Wikimedia Commons. Les originaux de Badr Al-Turki (1 010 × 1 280), Al-Kouchi (600 × 915) et Maher Al-Muaiqly (405 × 403) remplacent les miniatures. Les logos et les portraits sont décodés selon la taille affichée. Une icône de livre accompagne les récitateurs sans portrait disponible sous licence de réutilisation.

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
| سعد الغامدي | الشيخ هيثم الدخين, CC BY-SA 4.0 | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Saad_al_Ghamdi.jpg) |
| ناصر القطامي | Ayman dhū alghiná, CC BY-SA 4.0 | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:صورة_شخصية_الشيخ_ناصر_القطامي.jpg) |
| صلاح البدير | Salah Al Budair, CC BY-SA 4.0 | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Salah_Ibn_Mohammed_Al_Budair.jpg) |
| عبد المحسن القاسم | শেখ শরীফ, CC BY 4.0 | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Abdul_Mohsin_Al-Qasim.jpg) |
| علي الحذيفي | آيات, CC BY 3.0 | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Ali_al-Hudhayfi.jpg) |

**هشام الهراز (Hicham El Herraz)** est accessible dans **القرآن → الترتيل**. La recherche accepte aussi « Hicham Hezzaz ». Le lecteur enchaîne les 113 sourates en Warsh publiées par MP3Quran. La sourate 33, absente de cette édition, est passée. [Recueil officiel](https://www.mp3quran.net/ar/hsh-m-lhr-z)

## Vérification des sources de Radio 2M et de la SNRT

Le flux de Radio 2M correspond au champ audio `link_ecouter` du service utilisé par son site, sur le CDN de son lecteur. Le manifeste, sa variante et un segment audio ont répondu. Le segment contrôlé contenait de l’AAC décodable. Le titre d’émission était absent de la réponse contrôlée. [Lecteur Radio 2M](https://radio2m.ma/) · [Données officielles du direct](https://bo.radio2m.ma/api/live).

Les quatre pages de SNRT Live publient des flux HLS EasyBroadcast distincts. Une requête directe sans jeton reçoit HTTP 403. Avec un jeton temporaire obtenu auprès du service du lecteur officiel, les quatre manifestes, leurs variantes et des segments audio ont répondu lors des essais. L’application garde le jeton en mémoire jusqu’au renouvellement et ne transmet les paramètres qu’aux listes HLS correspondantes. [Radio Nationale](https://snrtlive.ma/fr/alidaa-alwatania) · [Radio Amazighe](https://snrtlive.ma/fr/alidaa-alamazighia) · [Chaîne Inter](https://snrtlive.ma/fr/chaine-inter) · [Mohammed VI du Saint Coran](https://snrtlive.ma/fr/idaat-mohammed-assadiss).

## Vérifications de la version 2.5.44

**Fermeture à l’ouverture sur un téléphone.** La 2.5.43 se fermait au lancement sur le téléphone du propriétaire. Sur l’émulateur Android 13 en format téléphone (1080 × 2340), ni l’installation neuve, ni la mise à jour depuis la 2.5.42, ni le mode Système de nuit n’ont reproduit la fermeture. Deux changements visent les causes possibles : le récepteur des boutons du mini-lecteur n’est enregistré que sur l’Incarlink, et protégé (`miniReceiverRegistered`) ; `followSystemNight` ne recrée l’écran qu’une fois par tranche de 10 s, et une erreur de `SystemNight` retombe sur la configuration courante. Pour connaître la cause exacte, `RadioApp` installe un gestionnaire d’erreurs qui enregistre la trace (version, Android, modèle) dans `last-crash.txt`. Au lancement suivant, `RadioActivity` passe la main à `CrashReportActivity`, écran indépendant qui affiche la trace, la partage par `ACTION_SEND` et relance la radio. Vérifié sur l’émulateur avec `am crash` : rapport affiché, puis radio relancée. Les tests unitaires ont réussi et lint a signalé 0 erreur.

## Vérifications de la version 2.5.43

**Liste Radio Maroc.** MFM Radio (flux du lecteur officiel de mfmradio.ma) et Atlantic Radio (flux qui s’annonce Atlantic Radio et renvoie vers atlanticradio.ma) entrent au catalogue ; leurs certificats Let’s Encrypt sont valides et ils ne sont plus filtrés par `StationDirectory`. Le tri « Popularité » de `Stations.visible` place d’abord les stations de `popularityOrder` (Radiométrie, puis Luxe Radio, U Radio, Atlantic Radio, Radio Plus, Radio Tanger Med et Marrakech Plus), ensuite les autres stations du catalogue, enfin les webradios de l’annuaire par nombre de clics. Les webradios de l’annuaire sont dédoublonnées par flux (`streamKey`) puis par nom ; Kabana Rooftop Marrakech, « Fayroz » et les chaînes secondaires de Radio Adwaa sont écartées (`rejectedMoroccoNames`). Fayrouz quitte l’onglet Maroc mais reste dans `Stations.all` pour les favoris. `RadioMotion.isLightOnClear` donne une tuile sombre aux logos blancs sur fond transparent. Détails : [CATALOGUE-AUDIT.md](CATALOGUE-AUDIT.md). Sur l’émulateur Android 13 en 800 × 480, l’ordre affiché a été relevé, puis MFM Radio et Atlantic Radio ont joué en direct. Les tests unitaires ont réussi et lint a signalé 0 erreur.

**Maps, YouTube et mini-lecteur (Incarlink).** Sur les écrans voiture (`carScreen`), la rangée du lecteur ajoute deux boutons après l’étoile : Maps et YouTube (`CompanionApp`). Ils lancent l’application installée par son intent de lancement public : Google Maps ou Waze, sinon toute application qui ouvre `geo:` ; Google YouTube ou un client courant sur les autoradios (ReVanced, YouTube Music, SmartTube, NewPipe). Les paquets sont déclarés dans `<queries>`. En écran partagé, l’application s’ouvre à côté de la radio (`FLAG_ACTIVITY_LAUNCH_ADJACENT`) ; sinon la radio passe en mini-lecteur, la fenêtre picture-in-picture d’Android, au format 16:9 : le logo à gauche sur une lueur légère de sa couleur et un reflet de verre dépoli (Android dessine cette fenêtre opaque : un fond transparent y laisse un gris et des restes d’images, essayé sur l’émulateur), le nom, l’émission ou l’état (« Connexion au flux… », « En pause »), un badge LIVE et `EqualizerBars`, ouverte depuis le cadre du lecteur (`setSourceRectHint`), avec trois `RemoteAction` (précédente, lecture / pause, suivante) reçues par un `BroadcastReceiver` non exporté. Le mini-lecteur s’ouvre aussi en quittant la radio pendant l’écoute (`setAutoEnterEnabled` sur Android 12+, `onUserLeaveHint` avant) ; **Réglages › Mini-lecteur** le désactive (`mini_player`). Aucune application n’est intégrée dans la radio. Le téléphone n’a ni boutons ni mini-lecteur. Sur l’émulateur Android 13 en 800 × 480, avec un lien de test vers les Paramètres d’Android (retiré ensuite), la radio est passée en mini-lecteur pendant l’ouverture, le bouton suivant a changé de station et l’agrandissement a rendu la radio en plein écran. Sans Maps ni YouTube installés, chaque bouton affiche « … n’est pas installé ». Les deux applications restent à essayer sur l’Incarlink.

## Vérifications de la version 2.5.42

**Jour / Nuit › Système.** Sur un téléphone, la radio restait en Jour alors qu’Android était passé en sombre. En Système, l’application demandait seulement à Android de la faire suivre le système (`setApplicationNightMode(MODE_NIGHT_AUTO)`), ce que cet appareil n’a pas appliqué. Elle demande toujours ce suivi, pour réagir aussitôt là où il fonctionne, mais `attachBaseContext` applique maintenant l’état lu par `SystemNight` : le réglage `UiModeManager.getNightMode()` d’Android, qu’aucun réglage propre à l’application ne modifie. Toujours actif ou désactivé se lit directement ; l’horaire personnalisé avec `customNightModeStart` et `customNightModeEnd` ; « du coucher au lever du soleil » par un calcul solaire pour Casablanca. `followSystemNight` revérifie au retour à l’écran puis chaque minute et recrée l’écran si l’état diffère. Sur l’émulateur Android 13 en 800 × 480, la radio en Système est passée de Jour à Nuit puis à Jour en moins de 15 s avec `cmd uimode night`. Les tests unitaires (`SystemNightTest`) ont réussi et lint a signalé 0 erreur. Le téléphone où le défaut a été constaté reste à vérifier.

**Entête en Jour.** Sur téléphone, les onglets n’ont pas de fond et laissent voir le fond animé ; sur grand écran, ils sont dans une capsule `radio_segment`, à côté des pastilles `radio_chip` (météo, salat) et du bouton `radio_header`, toutes remplies du blanc à 78 % des commandes, presque opaque. En Jour, `drawable-notnight` remplace ces trois formes par le même verre que la barre d’actualités (blanc de 40 à 22 %, liseré blanc). Nuit et Incarlink ne changent pas. Vérifié sur l’émulateur en 800 × 480.

## Vérifications de la version 2.5.41

**Herraz en direct.** La récitation de Hicham El Herraz tourne comme une radio. Les durées des 113 fichiers MP3Quran (128 kbit/s, 25 h 35 min au total) sont mesurées dans `HerrazRecitations` ; `live()` calcule la sourate et la position du moment, la liste boucle et une reprise après plus de 30 s de pause rejoint le direct. Une sourate cherchée par son nom démarre toujours à son début. Le point de reprise et ses boutons « Reprendre » sont retirés. Sur l’émulateur, Herraz a démarré à la sourate As-Saff, la position calculée pour l’heure de l’essai, avec le badge LIVE.

**Récitateurs.** L’onglet Coran propose 33 radios MP3Quran en tartil, classées par notoriété (`quranPopularity` dans `Stations`) : Alafasy, Abdul Basit, Al-Soudais, Al-Mueaqly et Yasser Al-Dosari en tête, puis les récitateurs marocains. Alafasy, Al-Soudais et Al-Dosari, exclus sans raison consignée, reviennent. Sept récitateurs et Abdul Basit en Warsh sont ajoutés. Chaque flux a répondu en `audio/mpeg` le 15 novembre 2026 ; le serveur refuse les requêtes trop nombreuses en parallèle, elles ont donc été contrôlées une à une. Idrees Abkar, en erreur 500, n’est pas ajouté.

**Animations.** `RadioMotion` regroupe les effets : à chaque changement de station, le logo du lecteur entre avec un zoom amorti et le titre glisse depuis le côté du saut (suivant, précédent, ou par le bas pour un choix direct). `PlayerGlow` superpose au fond du lecteur un dégradé radial dans la couleur dominante du logo, en fondu entre deux stations, du côté du logo pour les noms arabes. Les tuiles de Présélections (`radio_tile`, coins de 18 dp et léger dégradé) portent aussi une lumière radiale dans la couleur de leur logo, plus forte sur la station choisie ; les couleurs mesurées sont gardées en mémoire pour le défilement. Dans le catalogue tactile, les lignes deviennent des cartes arrondies teintées depuis le côté du logo (`litCard`, `RadioMotion.paintRow`), hautes de 8 dp de plus, avec un logo plus grand ; la station en écoute porte un égaliseur animé (`EqualizerBars`) à la place de l’icône de lecture. Les logos ont des coins arrondis. Les tuiles et boutons ont un léger enfoncement au toucher. Rien n’est animé quand Android désactive les animations. Sur l’émulateur, la lueur rouge de Med Radio puis celle de Hit Radio, les tuiles de Présélections en Nuit et en Jour et le catalogue du Cockpit ont été vérifiés.

**Luxe Radio.** Luxe Radio joue dans sa propre application. Si Android redémarrait le service de lecture pendant son écoute, `PlaybackService` ne la suivait plus et la station choisie ensuite ne l’arrêtait pas. Le service garde maintenant `luxe_started` dans les préférences ; avant toute autre station, y compris via suivant et précédent, `LuxeRadio.stop()` met Luxe Radio en pause, en se reconnectant brièvement à son service média si besoin.

**Jour.** La palette Jour (`res/values-notnight/radio_palette.xml`) quitte le bleu : fond ivoire en dégradé chaud, panneaux blancs, bordures grège, boutons, onglet actif et sélection en noir encre, accent vert émeraude pour les titres sélectionnés, le tri et l’égaliseur. Le lecteur passe du blanc au sable, sous la lueur du logo. Les panneaux, puces et tuiles sont translucides (alpha de 65 à 90 %) ; derrière eux, `AmbientBackdrop` fait dériver trois halos radiaux émeraude, ambre et corail sur le dégradé ivoire, sur un cycle de 18 s, en déplaçant seulement la matrice de leurs shaders, à environ 25 images par seconde. L’animation s’arrête quand l’écran est caché (0 image dessinée en arrière-plan sur l’émulateur) ou quand Android coupe les animations ; les dialogues restent opaques, et la télévision garde le fond fixe. La barre d’actualités du lecteur prend en Jour une bande de verre plus claire encore (`drawable-notnight/radio_news_bar.xml`, blanc à 38 puis 20 %) et sa pastille de source une teinte encre transparente, pour se fondre dans le lecteur ; ailleurs, `radio_news_bar` reprend `radio_control`. Sur l’émulateur, Pour vous, Maroc et Coran ont été vérifiés en Jour.

**Onglet Maroc.** La rangée de thèmes (Toutes, Infos & débats, Sport, Hits…) sous la recherche est retirée à la demande du propriétaire ; la liste gagne sa hauteur. `activeCategory()` renvoie toujours `MoroccoCategory.ALL`, si bien qu’un thème enregistré par une ancienne version ne masque plus de stations. Les rangées du Coran, du Monde et des Favoris restent.

**Pour vous, Coran, Favoris.** Un récitateur sans portrait n’a plus l’icône de livre : `StationArtwork` affiche la première lettre de son nom en blanc sur une tuile émeraude, et la lueur du lecteur prend ce vert. Le sous-titre par défaut des radios MP3Quran devient « ترتيل · بث مباشر · MP3Quran » (et « بث مباشر » pour le tajwid et Badr). Dans Pour vous et Favoris, les notes « Écoutée récemment » et « Présélection N » reçoivent le genre de la station (`Stations.shortKind`), par exemple « Présélection 1 · Débats ». La puce « Organiser les présélections » devient « Organiser », et la rangée de puces ne défile plus que si la puce choisie sort de l’écran, du strict nécessaire, si bien que la première n’est plus coupée. Vérifié en Jour sur l’émulateur.

**Placer les stations.** Dans **Favoris › Organiser**, et dans les onglets **Maroc** et **Coran** avec **Trier › Mon ordre** (`StationSort.CUSTOM`), une fenêtre liste les stations avec leur logo. On tient la poignée ≡ d’une station et on la fait glisser : elle suit le doigt, la liste défile seule près des bords, et l’ordre est enregistré au lâcher (`order_MOROCCO`, `order_QURAN`, ou `favorite_order` pour les présélections). Les flèches monter / descendre restent pour la télécommande, le volant et TalkBack. En « Mon ordre », une puce **≡ Placer les stations** rouvre la fenêtre, et **Rétablir l’ordre par popularité** efface l’ordre choisi. Les stations jamais placées suivent l’ordre par popularité. La liste est un `ListView` qui ne construit que les lignes visibles, pour les centaines de stations du Maroc. Pour l’ouvrir vite, `RadioCatalogue` compile désormais ses expressions régulières une seule fois (`searchKey` en recompilait une à chaque appel, des milliers de fois par tri) et le tri par audience mesure chaque station une seule fois ; le démarrage à froid sur l’émulateur passe d’environ 11 s à 6 s. Sur l’émulateur, Medi 1 Radio a été glissée en tête du Maroc, et HIT RADIO tenue contre le bord bas a descendu la liste.


**Picons.** Cinq récitateurs reçoivent leur portrait Wikidata (P18), vérifié sur Wikimedia Commons le 15 novembre 2026 et crédité dans l’application comme les autres : Mishary Alafasy (« Copyrighted free use », quranic.ru), Abdul Rahman Al-Soudais (GODL-India, Prime Minister’s Office), Hazza Al-Balushi (CC BY-SA 4.0, Ammar molla), Ali Jaber (CC0, Engalali9) et Mustafa Ismail (domaine public, Riad Shehata). Les douze autres récitateurs sans portrait libre (dont Omar Al-Qazabri et Hicham El Herraz) gardent leur tuile émeraude à l’initiale. Tarab Radio reçoit le logo publié pour son flux dans l’annuaire Radio Browser. Radio Manarat et Fayrouz ne publient aucun logo utilisable : comme toute station sans logo, y compris dans l’onglet Monde, elles affichent désormais une tuile en dégradé, d’une couleur propre à leur nom, avec leurs initiales en blanc.

Sur l’émulateur Android 13 en 800 × 480, sans Luxe Radio, l’ordre de l’onglet Coran, le direct de Herraz et le passage à Med Radio ont été vérifiés. Les tests unitaires ont réussi et lint a signalé 0 erreur. Le passage de Luxe Radio à une autre station reste à vérifier sur l’Incarlink.

## Vérifications de la version 2.5.40

Dans **Présélections**, le badge d’état du lecteur ancré en bas passe sous le nom de la station, sur une ligne. À côté des boutons ronds, « Connexion au flux… » se coupait en plein mot. Sur l’émulateur Android 13 en 800 × 480, « Connexion au lecteur… » puis le badge LIVE ont été vérifiés en Incarlink et en Nuit. Les tests unitaires ont réussi et lint a signalé 0 erreur. Le test sur l’Incarlink reste à faire.

## Vérifications de la version 2.5.39

En style **Incarlink**, la vue **Présélections** reprend les tuiles de l’accueil du boîtier : logo sur une face sombre, nom sur une bande gris clair (`radio_tile`, `radio_tile_selected`), et commandes du lecteur du bas sur la bande claire `radio_control_band_end`. Les versions par défaut de ces formes reproduisent l’ancien rendu, si bien que Jour, Nuit et Système ne changent pas. Sur l’émulateur Android 13 en 800 × 480, Présélections a été vérifié en Incarlink sur les onglets Maroc et Monde, puis en Nuit. Les tests unitaires ont réussi et lint a signalé 0 erreur. Le test sur l’Incarlink reste à faire.

## Vérifications de la version 2.5.38

Le style **Incarlink** reprend la tuile média de l’accueil du boîtier : lecteur anthracite aux coins de 22 dp, commandes plates sur une bande gris clair en bas du Cockpit, liste et en-tête en tuiles arrondies. Ces formes se trouvent dans `res/drawable-car` ; la bande `radio_control_band` reste transparente pour les autres apparences. Sur l’émulateur Android 13 en 800 × 480, le Cockpit a été vérifié en Incarlink puis en Nuit, qui ne change pas. Les tests unitaires ont réussi et lint a signalé 0 erreur. Le test sur l’Incarlink reste à faire.

## Vérifications de la version 2.5.37

**Réglages › Jour / Nuit** propose un quatrième choix, **Incarlink**, qui reprend l’écran d’accueil du boîtier : fond presque noir, panneaux anthracite, texte blanc et accent vert menthe. Sa palette se trouve dans `res/values-car` ; l’activité seule passe en mode d’interface voiture sombre pour la charger, sans toucher au réglage du système. Sur un appareil qui serait déjà en mode voiture, les autres choix forcent le mode normal pour garder leur palette. Sur l’émulateur Android 13 en 800 × 480, le Cockpit, les Réglages et le passage d’Incarlink à Nuit ont été vérifiés. Les tests unitaires ont réussi et lint a signalé 0 erreur. Le test sur l’Incarlink reste à faire.

## Vérifications de la version 2.5.36

Au démarrage et à chaque réouverture, la radio relance la dernière station écoutée. Ce comportement est désormais le réglage par défaut de **Démarrage voiture** ; un choix déjà enregistré est conservé. Sur l’émulateur Android 13 en 800 × 480, Medina FM a repris en direct après un arrêt complet de l’application, puis après une pause, un retour à l’accueil et une réouverture par l’icône. Le catalogue Maroc a été recontrôlé : les 41 flux directs ont répondu, Radio Nationale et SNRT Radio Casablanca ont joué dans l’application, Medina FM et Radio Azawan passent en 128 kbit/s et Medina FM Nostalgie est ajoutée. Les chaînes Hit Radio et Medi 1 commencent par leur propre nom dans les widgets média, coupés trop tôt sur l’Incarlink. Pendant le chargement du logo, les lecteurs externes reçoivent son adresse et non plus l’icône de l’application, qui pouvait y rester affichée. Le lecteur média d’Android a affiché le logo et le nouveau titre sur l’émulateur ; le widget de l’Incarlink reste à vérifier. Le classement suit la vague 53 de Radiométrie Maroc. Les 48 tests unitaires ont réussi et lint a signalé 0 erreur. Le test sur l’Incarlink reste à faire.

## Vérifications de la version 2.5.35

Sur l’émulateur Android 13 en 800 × 480, les vues Cockpit et Présélections ont été contrôlées à 160 et 213 dpi. L’onglet Pour vous, les thèmes de Radio Maroc, la sourate Al-Kahf lancée depuis la recherche, les raccourcis de pays, les présélections réordonnées, les boutons du volant limités aux présélections, les Réglages et la fenêtre Mises à jour ont été vérifiés. Après 70 s sans réseau, Med Radio a repris seule. Les 47 tests unitaires ont réussi. Lint a signalé 0 erreur et 73 avertissements. Le test sur l’Incarlink reste à faire.

La 2.5.35 est signée avec un nouveau certificat Radio Maroc (SHA-256 `b947b7ffbc84ea50e3b8810abaac1171bad745f6fc112da6a54f1fed1f1d1c3b`), la clé de la 2.5.34 n’étant plus disponible. Elle ne s’installe pas par-dessus la 2.5.34 : il faut désinstaller puis installer. Les versions suivantes se mettront à jour depuis la 2.5.35.

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
