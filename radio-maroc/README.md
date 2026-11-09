# Radio Maroc & Coran

Application Android 13+ indépendante pour l’Incarlink et les téléphones compatibles. Après l’installation de l’APK signé, ouvrir **Radio Maroc & Coran** depuis la grille d’applications de Quickstep. Une connexion Internet est nécessaire pour écouter.

La version **1.1.0** reprend l’apparence de Quickstep dans Radio, avec un fond ardoise, des cartes gris fumé arrondies et une sélection bleue. Installe-la sur la version 1.0.0 pour garder les favoris et la dernière station enregistrée.

- **Radio Maroc** affiche une sélection de 12 flux directs, notamment HIT RADIO, Radio Mars, Chada FM, Aswat, Med Radio, Skyrock Casablanca, Radio Plus Agadir et Medina FM.
- **القرآن** propose 12 flux de récitation, dont العيون الكوشي, عمر القزابري et محمود خليل الحصري en lecture Warsh. La sélection suit les [flux publiés par MP3Quran](https://www.mp3quran.net/api/v3/radios?language=ar).
- Les étoiles enregistrent les favoris. La recherche et les commandes précédente/suivante suivent la liste affichée. La dernière station est mémorisée pour une reprise manuelle. La lecture active continue en arrière-plan et peut être mise en pause depuis les commandes média Android.

Le catalogue est embarqué et les flux sont lus directement depuis leurs hébergeurs. Les lecteurs officiels de certains diffuseurs sont les sources de leurs URL publiques. Les autres entrées de la sélection marocaine proviennent de l’[annuaire Radio Browser](https://www.radio-browser.info/). Les stations et horaires peuvent évoluer. [HIT RADIO](https://www.hitradio.ma/live) · [Radio Mars](https://radiomars.ma/radio-live/) · [Chada FM](https://chada.ma/test-live/) · [Radio Aswat](https://radioaswat.ma/).

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
