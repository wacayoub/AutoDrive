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

## Contrôle du 14 novembre 2026 (après 2.5.35)

| Source contrôlée | Résultat |
| --- | --- |
| Catalogue intégré | Les 41 flux directs hors SNRT, Radio 2M et Luxe ont renvoyé de l’audio HTTPS au premier passage. Aucun flux n’est retiré. |
| SNRT et Radio 2M | Sans l’autorisation du lecteur officiel, 14 manifestes SNRT sur 15 ont répondu HTTP 403. Radio 2M a répondu avec les en-têtes de son lecteur. Dans l’application, Radio Nationale et SNRT Radio Casablanca ont atteint la lecture en direct. |
| Annuaire communautaire | Les adresses de MFM Radio, Atlantic Radio, Yabiladi et du second flux Chada FM échouent toujours à la vérification du certificat. Elles restent filtrées. Aucun flux HTTPS officiel de MFM ou d’Atlantic n’a été trouvé sur leurs sites. |

### Changements

- **Medina FM** lit désormais le flux 128 kbit/s du diffuseur au lieu de 64 kbit/s. **Radio Azawan** passe au flux MP3 128 kbit/s. Les identifiants de favoris ne changent pas.
- **Medina FM Nostalgie** rejoint le catalogue, dans le thème Chaâbi & scène marocaine. Le flux provient du même hébergement que les autres chaînes Medina FM.
- **Classement « Popularité »** : les six premières places suivent la vague 53 de Radiométrie Maroc (avril–juin 2025, audience quotidienne en semaine) : Radio Mohammed VI du Saint Coran 17,8 %, Med Radio 12,45 %, Hit Radio 12,25 %, MFM 10,55 %, Medi1 10,32 %, Al Idaâ Al Wataniya 10,10 %. Les autres stations mesurées gardent l’ordre des parts d’audience publiées précédemment. MFM prendra sa place dès qu’un flux HTTPS valide sera disponible. [Le Brief](https://www.lebrief.ma/pres-de-15-millions-de-marocains-ecoutent-la-radio-chaque-jour-100123781/) · [FNH](https://fnh.ma/article/actualites-marocaines/radio-maroc-2025)

## Catalogue et Smart TV

Le catalogue local comprend 59 entrées dans Radio Maroc, avec les résultats supplémentaires de l’annuaire selon le réseau. Le menu Genres propose les radios régionales, les entrées communautaires et la musique arabe, avec des filtres Fayrouz, Tarab, Chaâbi, Andalou et Scène marocaine. Amazigh a son propre filtre.

À partir de 2.5.17, le bandeau RFI du lecteur TV fait défiler les titres de cinq rubriques et actualise les flux toutes les quinze minutes pendant que l’application est à l’écran. Un appui ouvre les rubriques et leurs résumés. La lecture de la radio continue pendant la consultation. [Mode d’emploi des fils RSS de RFI](https://www1.rfi.fr/actufr/articles/072/article_40497.asp)
