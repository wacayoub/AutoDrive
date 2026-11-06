AutoDrive 1.7.0 améliore Téléphone pour l’Incarlink.

- Clavier Android réactivé pour saisir ou coller un numéro. Pavé, curseur, remplacement d’une sélection, touche 5 et effacement corrigés et vérifiés.
- BT Phone (`com.carsyso.bluetooth`) prioritaire en mode Automatique lorsqu’il est installé et accessible. Sans BT Phone détecté, aucun composeur SIM n’est lancé automatiquement. Les associations explicitement choisies restent respectées. L’icône de réglages permet de changer l’application.
- Onglets Contacts / Favoris avec recherche par nom ou numéro. Les contacts du fournisseur Android sont lus après autorisation, hors du thread UI, et actualisés lorsqu’ils changent. Aucun carnet n’est envoyé sur Internet ou enregistré par AutoDrive.
- Un contact remplit le numéro sans appeler. « Préparer l’appel » reste réservé aux applications recevant les numéros par l’intent standard Android. Sinon, « Copier et ouvrir » copie explicitement le numéro et ouvre l’application choisie, où il faut le coller.
- Disposition adaptée aux écrans 800 × 480 à densité élevée, sans réduire les touches sous 48 dp.

Limite importante : les contacts et les appels du téléphone Bluetooth ne sont pas automatiquement accessibles aux applications tierces. Si BT Phone garde son carnet privé, il faut utiliser son propre écran. Aucun appel Bluetooth direct ni synchronisation du mobile n’est annoncé comme vérifié sur le boîtier du propriétaire. L’audit constructeur et les limites sont dans docs/PHONE-INCARLINK-AUDIT.md.

Radio et conservation des cookies Maps/YouTube de 1.6.2 sont conservées. Installer en mise à jour, sans désinstaller ni effacer les données. La nouvelle autorisation Contacts n’est demandée qu’à l’appui sur « Autoriser les contacts ».

Mise à jour : Settings → Mises à jour → Rechercher → Télécharger → Installer. Android demande la confirmation d’installation.
