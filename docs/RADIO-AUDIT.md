# Audit Radio — suivi 1.8.0

Le propriétaire confirme que **précédente / suivante** restent inopérantes sur son Incarlink et que la liste est difficile à utiliser. Les causes exactes sur son matériel ne sont pas encore reproduites ; les constats suivants viennent du code et des essais sur émulateur.

| Constat 1.7.0 | Correction 1.8.0 |
| --- | --- |
| Chaque écoute ajoute la station à une bibliothèque limitée à 200 entrées. Une bibliothèque pleine empêche `playRadio` de changer vers une nouvelle station. | La dernière station est mémorisée séparément. Écouter ou changer de station ne remplit plus la bibliothèque et ne dépend plus de sa capacité. Les favoris/entrées manuelles existants sont conservés. Un refus d’ajout aux favoris produit désormais un message. |
| Sans catalogue/cache utilisable, la station initiale unique laisse précédente/suivante désactivées. | Trois stations de départ (HIT RADIO, Radio Mars, Radio Aswat) sont disponibles sans téléchargement d’annuaire. Le catalogue complet les remplace après chargement. Hors connexion, ces métadonnées ne constituent pas une lecture audio : une erreur réseau reste affichée. |
| Aucun MediaSession Android ne reçoit les commandes système. | Une session publique accompagne le lecteur actif et expose lecture, pause, arrêt, précédente et suivante, avec les vraies métadonnées/états du lecteur. Elle est libérée à sa fermeture. Cela ne prouve pas que les boutons Toyota/Incarlink sont transmis comme touches média Android. |
| Le mode compact masque le nom et l’état de la station. | Ces informations restent visibles. L’étoile filtre les favoris et la loupe ouvre le catalogue. |
| La grande liste laisse peu de stations visibles. | Grille à deux colonnes en paysage large, logos disponibles, noms sur deux lignes, étoile tactile séparée, recherche et filtres réunis sur une ligne. Le bandeau de lecture et ses commandes partagent une ligne. Sur un écran étroit, les commandes restent disposées en plusieurs lignes. |

## Sources des stations de départ

- HIT RADIO : [site officiel](https://hitradio.ma/?lang=fr), flux `https://hitradio-maroc.ice.infomaniak.ch/hitradio-maroc-128.mp3`.
- Radio Mars : [lecteur officiel](https://radiomars.ma/radio-live/), dont la balise audio utilise `https://radiomars.ice.infomaniak.ch/radiomars-128.mp3`.
- Radio Aswat : [page du direct officielle](https://radioaswat.ma/%D8%A7%D9%84%D9%85%D8%A8%D8%A7%D8%B4%D8%B1/), flux `https://broadcast.ice.infomaniak.ch/aswat-high.mp3`.

Le lien Aswat publié renvoie une redirection 302 vers HTTP. Son endpoint canonique `https://aswat.ice.infomaniak.ch/aswat-high.mp3` a été vérifié séparément (TLS, HTTP 200, `audio/mpeg`). Le lecteur utilise explicitement cet endpoint pour cet alias précis ; les redirections générales vers HTTP ne sont pas autorisées.

URLs publiques observées le 6 novembre 2026. Les flux et logos sont chargés depuis leurs serveurs ; aucun audio/logo n’est réhébergé. Leurs adresses et disponibilités peuvent changer. Les logos absents ou refusés utilisent des initiales. Le catalogue Radio Browser reste une liste communautaire non exhaustive ; les trois entrées de départ ne représentent pas toutes les stations marocaines.

Les boutons parcourent le catalogue actif, ou les favoris si ce filtre est choisi. Avec zéro ou un favori, précédente/suivante restent logiquement désactivées dans ce filtre. L’étoile de l’accueil permet de revenir à la liste Maroc. Une station refusée ou un réseau coupé ne sont pas présentés comme une lecture réussie.

Résultats mesurés et limites : [TEST-RESULTS.md](TEST-RESULTS.md). Aucun essai sur le boîtier du propriétaire ni accès au tuner FM Toyota n’est revendiqué. [API publique MediaSession.Callback](https://developer.android.com/reference/android/media/session/MediaSession.Callback).

---

# Audit Radio — 1.6.0 vers 1.6.1

Audit demandé le 6 novembre 2026 après le retour du propriétaire : liste sans icônes et boutons de changement de station inopérants. Les constats ci-dessous viennent du code 1.6.0 ; ils ne constituent pas un diagnostic du réseau du boîtier.

| Constat | Cause observée | Correction 1.6.1 |
| --- | --- | --- |
| Précédente/suivante désactivées après la première écoute | Les contrôles utilisent uniquement `radioPhone.stations`, qui ne contient initialement que HIT RADIO. Le catalogue Maroc n’est pas la file parcourue. | Une seule file cohérente pour la liste affichée et le changement de station ; charger le catalogue à l’ouverture de Radio. |
| Parcours limité à quelques stations après plusieurs choix | Une station du catalogue n’entre dans les stations enregistrées qu’après sa sélection. Le parcours dépend donc de l’historique de sélection. | Parcourir tout le catalogue chargé, ou les favoris lorsque ce filtre est sélectionné. |
| Liste uniquement textuelle, aucun logo | Le parseur ignore `favicon` ; les préférences et le cache ne stockent pas le logo ; les listes utilisent `ArrayAdapter` textuel. | Conserver les URL de logos, afficher des lignes avec image et état de sélection, utiliser des initiales si l’image manque ou échoue. |
| Un doublon sans logo masque une variante avec logo | `distinctBy(url)` conserve la première entrée même si une autre entrée du même flux porte une image. | Fusionner les métadonnées des doublons par URL. |
| Pause inefficace pendant la connexion | `toggle()` ne met en pause que l’état READY ; BUFFERING relance `play()`. L’icône dépend de `isPlaying`, faux pendant la connexion. | Suspendre selon `playWhenReady`, afficher Pause également durant la connexion et pouvoir reprendre explicitement. |
| Recherche et favoris difficiles à trouver | Deux listes modales différentes « Stations » et « Maroc », sans liste persistante dans le panneau. | Liste radio visible dans le panneau, filtres Maroc/Favoris et vue agrandie avec recherche. |
| Arrêt de la radio en quittant AutoDrive | Le lecteur est volontairement libéré par `onPause`, sans service audio de fond. | Comportement conservé et documenté ; cet audit ne prétend pas ajouter la lecture de fond ou les commandes au volant. |

Les logos et les flux dépendent de serveurs externes. Lors de l’audit, le logo officiel HIT RADIO répondait en HTTPS ; l’URL Radio Mars fournie par l’annuaire répondait HTTP 403 à la requête de contrôle. Le repli sans logo doit donc être testé. Un champ `favicon` dans un annuaire ne prouve ni la justesse d’un logo ni sa disponibilité. [Radio Browser — format des stations](https://docs.radio-browser.info/).

## Corrections et vérifications

- Liste persistante dans le panneau Radio, logos disponibles et initiales de remplacement, station choisie surlignée, état de lecture réel et étoiles de favoris. La loupe ouvre une vue agrandie avec recherche ; Maroc et Favoris partagent la même logique de file que les commandes.
- Trois tests JVM supplémentaires vérifient le catalogue complet avec une seule station enregistrée, les deux directions/bouclages, le filtrage des favoris et les métadonnées fusionnées. Les tests JVM de l’application comptent désormais 19 cas.
- Quatre tests instrumentés supplémentaires reproduisent ce scénario sur l’application réelle, attendent la lecture effective des flux HIT RADIO et Radio Mars, effectuent des changements rapides, vérifient les favoris vides puis deux favoris, la pause pendant la connexion, la recherche, la migration du cache et le chargement borné d’un logo réel avec repli pour une URL absente.
- Un essai manuel par toucher sur les commandes a changé Radio Mars vers Radio MFM, puis est revenu à Radio Mars. Radio MFM a atteint l’état réel de lecture. Cela ne valide pas tous les flux de l’annuaire.
- Les images sont décodées à 128 pixels maximum avec un cache mémoire de 2 Mio, au plus deux tâches IO concurrentes, un téléchargement limité à 512 Kio et un cache disque limité à 64 fichiers/12 Mio. Les erreurs d’image ne bloquent ni la liste ni la lecture. Les anciens logos manquants sont enrichis lors de l’actualisation du cache ; aucune préférence téléphone n’est effacée.
- L’audit visuel a aussi révélé qu’une recherche conservait sa position de défilement précédente : elle revient désormais au premier résultat. Le lecteur se replie pendant la saisie afin de laisser la liste visible.

Les validations Maps/YouTube existantes utilisaient des délais fixes ou une capture avant la fin d’une transition de fenêtre. Après des échecs intermittents de ces tests, leurs fixtures attendent leur DOM, leur rendu et, pour le contraste, les pixels effectifs de la fenêtre au premier plan. Aucun comportement de production Maps/YouTube n’a été modifié pour ces attentes de test.

Les résultats finaux de compilation, lint, scénarios hors connexion et captures sont consignés dans [TEST-RESULTS.md](TEST-RESULTS.md). Aucun résultat non exécuté n’est marqué réussi. Aucun essai de cette version sur le boîtier du propriétaire n’a été réalisé.
