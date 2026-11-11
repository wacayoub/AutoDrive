# Audit du catalogue Radio Maroc

Vérification pour Radio Maroc & Coran 2.5.16, le 11 novembre 2026. Les sondes ont lu jusqu’à 16 Kio par flux pour contrôler HTTPS, le format audio ou HLS et les métadonnées ICY disponibles. La lecture prolongée et la disponibilité depuis tous les réseaux restent à vérifier.

| Source contrôlée | Résultat |
| --- | --- |
| Catalogue intégré avant la mise à jour | 36 des 37 adresses hors SNRT et Luxe ont répondu avec de l’audio ou un manifeste HLS au premier passage. Le flux étiqueté « Radio Plus Agadir » s’identifiait comme `radioatb1`. |
| SNRT Live | Les 4 nationales et les 11 régionales ont renvoyé un manifeste HLS avec l’autorisation temporaire fournie par le lecteur SNRT Live. 14 réponses sur 15 étaient HTTP 403 sans cette autorisation. |
| Radio 2M | Le manifeste HLS publié par Radio 2M a répondu HTTP 200 avec les en-têtes de son lecteur. L’application les envoie déjà. |
| Annuaire communautaire | 57 entrées pour le Maroc, dont 49 adresses HTTPS sondées. Le contrôle a relevé 10 erreurs de certificat, une redirection vers HTTP, un fichier MP3 fixe et plusieurs doublons ou noms incompatibles avec les métadonnées. |

## Corrections

- **Fayrouz · فيروزيات** garde son flux Zeno et son identifiant de favori. La réponse ICY contenait « Fairouz - فيروزيات المساء ». La radio figure parmi les webradios indépendantes, sans attribution à un diffuseur marocain vérifié.
- **Aziz Mustaphi** est retiré des radios. L’adresse de l’annuaire sert un MP3 de taille fixe sans métadonnées de station.
- **Radio Plus** garde l’identifiant des favoris et lit le flux HTTPS qui s’identifie comme `RADIO PLUS`. Le flux régional d’Agadir reste à confirmer. La HACA répertorie plusieurs antennes locales du groupe. [HACA](https://www.haca.ma/fr/radio-plus-agadir)
- **MFM Radio, Atlantic Radio et Yabiladi** ont des adresses d’annuaire dont les certificats ont échoué. Ces adresses sont filtrées, y compris dans les anciens favoris et le cache. Aucun flux HTTPS de remplacement n’a été confirmé pour ces stations pendant ce contrôle. [MFM Radio](https://mfmradio.ma/), [Yabiladi](https://www.yabiladi.com/radio/)
- **Luxe Radio** conserve son intégration existante. Son accès via le service du diffuseur est hors de cette sonde HTTP.

## Catalogue et Smart TV

Le catalogue local comprend 59 entrées dans Radio Maroc, avec les résultats supplémentaires de l’annuaire selon le réseau. Le menu Genres propose les radios régionales, les entrées communautaires et la musique arabe, avec des filtres Fayrouz, Tarab, Chaâbi, Andalou et Scène marocaine. Amazigh a son propre filtre.

À partir de 2.5.17, le bandeau RFI du lecteur TV fait défiler les titres de cinq rubriques et actualise les flux toutes les quinze minutes pendant que l’application est à l’écran. Un appui ouvre les rubriques et leurs résumés. La lecture de la radio continue pendant la consultation. [Mode d’emploi des fils RSS de RFI](https://www1.rfi.fr/actufr/articles/072/article_40497.asp)
