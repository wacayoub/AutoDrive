# Conservation des sessions web — 1.6.2

Le propriétaire signale une déconnexion de son compte dans les panneaux Maps/YouTube après avoir quitté puis rouvert AutoDrive. Il a confirmé que le problème concerne la connexion, pas simplement une adresse e-mail absente de la saisie automatique. Le parcours exact de connexion et la cause sur son appareil restent non reproduits avec un compte Google réel.

## Audit du code 1.6.1

- Les panneaux utilisent le profil WebView privé de l’application. Aucune suppression explicite des cookies, du cache ou du stockage des sites n’a été trouvée dans leur cycle de vie.
- Les WebViews sont détruits puis reconstruits lors de la sortie ou d’un changement d’onglet, pour arrêter les médias et libérer les ressources. Cela ne prouve pas, à soi seul, une suppression des cookies.
- Le stockage DOM était déjà activé, mais les cookies n’étaient pas configurés explicitement. Android désactive par défaut les cookies tiers pour les applications ciblant Android 5 et ultérieur. Aucune sauvegarde explicite `CookieManager.flush()` n’était faite avant la fermeture. Ces deux points sont des facteurs possibles, pas une preuve de la cause de la déconnexion Google signalée.
- Les domaines de connexion Google hors des origines autorisées peuvent être ouverts dans le navigateur externe après une action utilisateur. Ce navigateur a sa propre session ; une connexion externe ne connecte pas automatiquement le panneau AutoDrive.

## Modification

`WebSessionStorage` configure les cookies normaux et les cookies tiers sur les deux panneaux, avant leur navigation. Les cookies tiers restent soumis aux choix de consentement et aux règles du navigateur/service. Le profil WebView existant est réutilisé, sans réinitialisation du compte ou des préférences.

Un enregistrement des cookies sur disque est demandé après le chargement d’une page, la sauvegarde du panneau, sa mise en pause/libération et l’arrêt de l’activité. `flush()` peut faire de l’IO : les demandes sont exécutées sur un dispatcher IO à parallélisme 1, sans référence conservée à l’Activity. L’écriture peut ainsi se terminer après la destruction de la vue, tant que le processus existe. Aucun identifiant, mot de passe, cookie ou jeton n’est lu, journalisé ou exporté par le code de production.

Les dates d’expiration, suppressions et règles des cookies restent celles du service. AutoDrive ne transforme pas des cookies de session en cookies permanents et ne réinjecte pas une sauvegarde après une déconnexion. Le comportement de libération des vues est conservé : cette version ne laisse pas une vidéo YouTube jouer en arrière-plan pour garder un compte connecté.

[API CookieManager : acceptation et flush](https://developer.android.com/reference/android/webkit/CookieManager) · [WebView : sauvegarde de l’historique](https://developer.android.com/reference/android/webkit/WebView#saveState(android.os.Bundle))

## Résultats et limites

Les tests n’utilisent que deux origines réservées `.example`, des cookies synthétiques `Secure; HttpOnly; SameSite=None; Max-Age=3600` et des valeurs sans données personnelles. Ils vérifient la politique des cookies et la conservation des cookies/localStorage après libération puis recréation des deux types de panneaux.

Deux exécutions séparées, avec arrêt forcé du processus entre écriture et lecture, ont retrouvé les cookies persistants des deux origines. L’assertion contrôle aussi que les PID sont différents : 2762 à l’écriture, 2909 à la lecture lors du contrôle. Le stockage local DOM n’a survécu que pour une des deux origines dans cette tentative d’arrêt immédiat. Un premier essai qui exigeait également la durabilité des deux écritures DOM a donc échoué ; le test de frontière de processus vérifie désormais explicitement les cookies et relève séparément le résultat DOM, sans prétendre que celui-ci est garanti.

Il n’existe pas d’API publique WebStorage de vidage immédiat sur disque. Une fermeture forcée peut interrompre une écriture DOM récente. Aucun contournement par extraction et recopie du stockage Google n’a été ajouté. Un arrêt normal avec libération/recréation des panneaux a conservé les données testées.

**Ces tests ne prouvent pas qu’un compte Google réel reste connecté.** Chrome, les applications Maps/YouTube installées et les WebViews n’utilisent pas nécessairement la même session. Google interdit certains parcours OAuth dans les navigateurs intégrés ; AutoDrive ne modifie pas son User-Agent, ne copie pas de cookies du navigateur et ne contourne pas ce refus. Un site peut aussi expirer ou révoquer une session. [Isolation WebView/navigateur](https://developer.chrome.com/docs/android/custom-tabs) · [Règles Google OAuth](https://developers.google.com/identity/protocols/oauth2/policies#secure-browsers).

Après mise à jour, une nouvelle connexion peut être nécessaire si les anciens cookies n’étaient plus présents. Seul un essai avec le parcours réel du propriétaire permettra de confirmer la persistance de son compte. Ne pas effacer les données d’AutoDrive ou le réinstaller en supprimant ses données pendant ce contrôle.
