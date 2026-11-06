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
