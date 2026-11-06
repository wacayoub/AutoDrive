AutoDrive 1.6.2 renforce la conservation des cookies des panneaux Maps et YouTube après fermeture/réouverture.

- Cookies normaux et tiers explicitement autorisés dans les deux panneaux, sans réinitialiser le profil WebView existant.
- Écriture des cookies sur disque après chargement et à la fermeture, hors du thread d’affichage. Les identifiants, mots de passe et jetons ne sont pas lus ni exportés par l’application.
- Deux tests de persistance ajoutés et vérification séparée après arrêt forcé dans un nouveau processus. Les cookies persistants synthétiques sont conservés sur les deux origines de test. Les données DOM très récentes ne sont pas toutes garanties après un arrêt forcé immédiat.
- Les sessions de Chrome et des applications installées restent séparées du panneau. Aucun contournement des restrictions de connexion Google n’est ajouté. La connexion réelle du propriétaire à Google reste à valider ; ces tests ne la prouvent pas.
- Radio, Téléphone et interface conservent le fonctionnement de 1.6.1. Une nouvelle connexion peut être nécessaire après mise à jour si les anciens cookies n’étaient plus présents.

Contrôles et limites : voir docs/WEB-SESSIONS.md et docs/TEST-RESULTS.md dans le dépôt. Aucun compte Google réel ni appareil du propriétaire n’a été utilisé pour les tests.

Mise à jour depuis Settings → Mises à jour → Rechercher → Télécharger → Installer. La signature et les préférences existantes sont conservées ; Android demande la confirmation d’installation.
